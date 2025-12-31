package com.aicommander.llm;

import com.aicommander.cache.PlanCache;
import com.aicommander.config.ModConfig;
import com.aicommander.planning.MinimalWorldStateCollector;
import com.aicommander.router.CommandRouter;
import com.aicommander.util.ChatUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Two-stage LLM planner for faster response times.
 *
 * Stage A: Fast parse with small model to determine intent and confidence.
 * Stage B: Full planning with larger model (only if needed).
 *
 * Also integrates:
 * - CommandRouter for instant deterministic execution
 * - PlanCache for avoiding redundant LLM calls
 */
public class TwoStagePlanner {

    private static final Gson GSON = new Gson();
    private static final int MAX_ACTIONS = 8;

    private final OllamaClient ollamaClient;
    private final CommandRouter router;
    private final PlanCache cache;
    private final MinimalWorldStateCollector stateCollector;
    private final ModConfig config;

    // Stage A prompt template (kept very short)
    private static final String STAGE_A_PROMPT = """
            You are a Minecraft command parser. Parse the user's intent into JSON.

            Current state: %s

            User request: "%s"

            Respond with ONLY this JSON (no other text):
            {"intent":"<action>","confidence":<0.0-1.0>,"target":{"type":"<block|entity|location|player>","id":"<minecraft:id>","name":"<name>","x":<x>,"y":<y>,"z":<z>},"count":<number>,"needs_planning":<true|false>,"reason":"<why>"}

            Intents: goto, mine, follow, explore, farm, stop, craft, build, attack, unknown
            Set needs_planning=true if the request is complex, ambiguous, or requires multiple steps.
            Set confidence high (0.8+) only if intent is clear and unambiguous.
            """;

    // Stage B prompt template (full planning)
    private static final String STAGE_B_PROMPT = """
            You are an AI controlling a Minecraft player via Baritone. Plan actions to accomplish the goal.

            CURRENT STATE:
            %s

            USER REQUEST: %s

            AVAILABLE ACTIONS:
            - {"type":"goto","x":<int>,"y":<int>,"z":<int>} or {"type":"goto","block":"<minecraft:id>"}
            - {"type":"mine","block":"<minecraft:id>","count":<int>}
            - {"type":"follow","target":"<player_name or entity_type>"}
            - {"type":"explore"}
            - {"type":"farm","range":<int>}
            - {"type":"stop"}
            - {"type":"equip","slot":<0-8>} or {"type":"equip","item":"<minecraft:id>"}
            - {"type":"wait","count":<seconds>}
            - {"type":"ask","question":"<clarification needed>"}

            RULES:
            - Use full minecraft IDs (minecraft:diamond_ore, not just diamond)
            - Maximum %d actions per plan
            - If unclear, use "ask" action to clarify

            Respond with ONLY this JSON:
            {"actions":[<action objects>],"chat_summary":"<short description for player>","reason":"<your reasoning>"}
            """;

    public TwoStagePlanner(OllamaClient ollamaClient, MinimalWorldStateCollector stateCollector) {
        this.ollamaClient = ollamaClient;
        this.stateCollector = stateCollector;
        this.router = new CommandRouter();
        this.cache = new PlanCache();
        this.config = ModConfig.getInstance();
    }

    /**
     * Plan actions for the given instruction.
     * Uses fast-path routing, caching, and two-stage LLM when needed.
     */
    public CompletableFuture<LLMResponse> planActions(String instruction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return planActionsInternal(instruction);
            } catch (Exception e) {
                throw new RuntimeException("Planning failed: " + e.getMessage(), e);
            }
        });
    }

    private LLMResponse planActionsInternal(String instruction) throws Exception {
        // 1. Try router (instant, no LLM)
        CommandRouter.RouterResult routerResult = router.tryRoute(instruction);
        if (routerResult.handled) {
            ChatUtils.sendDebug("FAST PATH: " + routerResult.message);
            return routerResult.response;
        }

        // 2. Check cache
        LLMResponse cached = cache.get(instruction);
        if (cached != null) {
            ChatUtils.sendDebug("Cache hit");
            return cached;
        }

        // 3. Collect world state (in parallel with LLM call preparation)
        String compactState = stateCollector.collectStateCompact();

        // 4. Stage A: Fast parse
        String stageAModel = config.stageAModelOverride != null ?
                config.stageAModelOverride : config.getActivePreset().getStageAModel();

        ChatUtils.sendDebug("Stage A: " + stageAModel);

        StageAResponse stageA = null;
        try {
            stageA = executeStageA(instruction, compactState, stageAModel);
        } catch (Exception e) {
            ChatUtils.sendDebug("Stage A failed: " + e.getMessage());
            // Fall through to Stage B
        }

        // 5. Check if Stage A is sufficient
        if (stageA != null && stageA.isHighConfidence()) {
            LLMResponse response = convertStageAToResponse(stageA, instruction);
            if (response != null) {
                ChatUtils.sendDebug("Stage A sufficient (conf=" + stageA.getConfidence() + ")");
                cache.put(instruction, response, true);
                return response;
            }
        }

        // 6. Stage B: Full planning
        String stageBModel = config.stageBModelOverride != null ?
                config.stageBModelOverride : config.getActivePreset().getStageBModel();

        ChatUtils.sendDebug("Stage B: " + stageBModel);

        LLMResponse response = executeStageB(instruction, compactState, stageBModel);

        // Enforce max actions
        if (response != null && response.getActions() != null &&
                response.getActions().size() > MAX_ACTIONS) {
            List<LLMResponse.Action> truncated = new ArrayList<>(
                    response.getActions().subList(0, MAX_ACTIONS));
            response.setActions(truncated);
            ChatUtils.sendWarning("Plan truncated to " + MAX_ACTIONS + " actions");
        }

        // Cache successful response
        if (response != null) {
            cache.put(instruction, response, true);
        }

        return response;
    }

    private StageAResponse executeStageA(String instruction, String state, String model)
            throws Exception {
        String prompt = String.format(STAGE_A_PROMPT, state, instruction);

        String response = ollamaClient.generate(model,
                prompt,
                config.getActivePreset().getStageATemperature(),
                config.ollamaTimeoutSeconds);

        // Extract JSON from response
        String json = extractJson(response);
        if (json == null) {
            throw new Exception("No valid JSON in Stage A response");
        }

        return GSON.fromJson(json, StageAResponse.class);
    }

    private LLMResponse executeStageB(String instruction, String state, String model)
            throws Exception {
        String prompt = String.format(STAGE_B_PROMPT, state, instruction, MAX_ACTIONS);

        String response = ollamaClient.generate(model,
                prompt,
                config.getActivePreset().getStageBTemperature(),
                config.ollamaTimeoutSeconds);

        // Extract JSON from response
        String json = extractJson(response);
        if (json == null) {
            throw new Exception("No valid JSON in Stage B response");
        }

        LLMResponse llmResponse = GSON.fromJson(json, LLMResponse.class);
        if (llmResponse == null || !llmResponse.isValid()) {
            throw new Exception("Invalid Stage B response structure");
        }

        return llmResponse;
    }

    /**
     * Convert a high-confidence Stage A response directly to an LLMResponse.
     */
    private LLMResponse convertStageAToResponse(StageAResponse stageA, String instruction) {
        String intent = stageA.getIntent();
        StageAResponse.Target target = stageA.getTarget();

        LLMResponse response = new LLMResponse();
        LLMResponse.Action action = new LLMResponse.Action();

        switch (intent) {
            case "goto" -> {
                action.setType("goto");
                if (target != null) {
                    if (target.hasCoordinates()) {
                        action.setX(target.getX());
                        action.setY(target.getY());
                        action.setZ(target.getZ());
                    } else if (target.getId() != null) {
                        action.setBlock(target.getId());
                    }
                }
                response.setChatSummary("Going to target");
            }
            case "mine" -> {
                action.setType("mine");
                if (target != null && target.getId() != null) {
                    action.setBlock(target.getId());
                }
                action.setCount(stageA.getCount() != null ? stageA.getCount() : 16);
                response.setChatSummary("Mining " + action.getCount() + " blocks");
            }
            case "follow" -> {
                action.setType("follow");
                if (target != null) {
                    action.setTarget(target.getName() != null ? target.getName() : target.getId());
                }
                response.setChatSummary("Following target");
            }
            case "explore" -> {
                action.setType("explore");
                response.setChatSummary("Exploring the area");
            }
            case "farm" -> {
                action.setType("farm");
                if (stageA.getCount() != null) {
                    action.setRange(stageA.getCount());
                }
                response.setChatSummary("Farming crops");
            }
            case "stop" -> {
                action.setType("stop");
                response.setChatSummary("Stopping");
            }
            default -> {
                // Can't convert this intent
                return null;
            }
        }

        response.setActions(Collections.singletonList(action));
        response.setReason(stageA.getReason());
        return response;
    }

    /**
     * Extract JSON object from LLM response text.
     */
    private String extractJson(String text) {
        if (text == null) return null;

        // Find the first { and last }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');

        if (start == -1 || end == -1 || end <= start) {
            return null;
        }

        String candidate = text.substring(start, end + 1);

        // Verify it's valid JSON
        try {
            GSON.fromJson(candidate, JsonObject.class);
            return candidate;
        } catch (JsonSyntaxException e) {
            // Try to balance braces
            return balanceBraces(text.substring(start));
        }
    }

    private String balanceBraces(String text) {
        int braceCount = 0;
        int startIndex = text.indexOf('{');

        if (startIndex == -1) return null;

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;

            if (braceCount == 0) {
                String result = text.substring(startIndex, i + 1);
                try {
                    GSON.fromJson(result, JsonObject.class);
                    return result;
                } catch (JsonSyntaxException e) {
                    return null;
                }
            }
        }

        return null;
    }

    /**
     * Check if Ollama is available.
     */
    public boolean isLLMAvailable() {
        return ollamaClient.isAvailable();
    }

    /**
     * Get cache statistics.
     */
    public String getCacheStats() {
        return cache.getStats();
    }

    /**
     * Clear the plan cache.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Set last failure reason for context.
     */
    public void setLastFailureReason(String reason) {
        stateCollector.setLastFailureReason(reason);
    }
}
