package com.aicommander.router;

import com.aicommander.llm.LLMResponse;
import com.aicommander.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast-path router that handles common commands deterministically without LLM.
 * This dramatically speeds up simple commands like stop, goto, mine, follow.
 */
public class CommandRouter {

    // Block aliases for common names -> minecraft IDs
    private static final Map<String, String> BLOCK_ALIASES = new HashMap<>();

    static {
        // Diamond variants
        BLOCK_ALIASES.put("diamond", "minecraft:diamond_ore");
        BLOCK_ALIASES.put("diamonds", "minecraft:diamond_ore");
        BLOCK_ALIASES.put("diamond ore", "minecraft:diamond_ore");
        BLOCK_ALIASES.put("diamond_ore", "minecraft:diamond_ore");

        // Iron variants
        BLOCK_ALIASES.put("iron", "minecraft:iron_ore");
        BLOCK_ALIASES.put("iron ore", "minecraft:iron_ore");
        BLOCK_ALIASES.put("iron_ore", "minecraft:iron_ore");

        // Gold variants
        BLOCK_ALIASES.put("gold", "minecraft:gold_ore");
        BLOCK_ALIASES.put("gold ore", "minecraft:gold_ore");
        BLOCK_ALIASES.put("gold_ore", "minecraft:gold_ore");

        // Coal variants
        BLOCK_ALIASES.put("coal", "minecraft:coal_ore");
        BLOCK_ALIASES.put("coal ore", "minecraft:coal_ore");
        BLOCK_ALIASES.put("coal_ore", "minecraft:coal_ore");

        // Copper variants
        BLOCK_ALIASES.put("copper", "minecraft:copper_ore");
        BLOCK_ALIASES.put("copper ore", "minecraft:copper_ore");
        BLOCK_ALIASES.put("copper_ore", "minecraft:copper_ore");

        // Redstone variants
        BLOCK_ALIASES.put("redstone", "minecraft:redstone_ore");
        BLOCK_ALIASES.put("redstone ore", "minecraft:redstone_ore");
        BLOCK_ALIASES.put("redstone_ore", "minecraft:redstone_ore");

        // Lapis variants
        BLOCK_ALIASES.put("lapis", "minecraft:lapis_ore");
        BLOCK_ALIASES.put("lapis lazuli", "minecraft:lapis_ore");
        BLOCK_ALIASES.put("lapis ore", "minecraft:lapis_ore");
        BLOCK_ALIASES.put("lapis_ore", "minecraft:lapis_ore");

        // Emerald variants
        BLOCK_ALIASES.put("emerald", "minecraft:emerald_ore");
        BLOCK_ALIASES.put("emerald ore", "minecraft:emerald_ore");
        BLOCK_ALIASES.put("emerald_ore", "minecraft:emerald_ore");

        // Ancient debris (netherite)
        BLOCK_ALIASES.put("netherite", "minecraft:ancient_debris");
        BLOCK_ALIASES.put("ancient debris", "minecraft:ancient_debris");
        BLOCK_ALIASES.put("ancient_debris", "minecraft:ancient_debris");
        BLOCK_ALIASES.put("debris", "minecraft:ancient_debris");

        // Wood types
        BLOCK_ALIASES.put("oak", "minecraft:oak_log");
        BLOCK_ALIASES.put("oak log", "minecraft:oak_log");
        BLOCK_ALIASES.put("oak logs", "minecraft:oak_log");
        BLOCK_ALIASES.put("oak_log", "minecraft:oak_log");
        BLOCK_ALIASES.put("oak wood", "minecraft:oak_log");

        BLOCK_ALIASES.put("birch", "minecraft:birch_log");
        BLOCK_ALIASES.put("birch log", "minecraft:birch_log");
        BLOCK_ALIASES.put("birch logs", "minecraft:birch_log");
        BLOCK_ALIASES.put("birch_log", "minecraft:birch_log");
        BLOCK_ALIASES.put("birch wood", "minecraft:birch_log");

        BLOCK_ALIASES.put("spruce", "minecraft:spruce_log");
        BLOCK_ALIASES.put("spruce log", "minecraft:spruce_log");
        BLOCK_ALIASES.put("spruce logs", "minecraft:spruce_log");
        BLOCK_ALIASES.put("spruce_log", "minecraft:spruce_log");
        BLOCK_ALIASES.put("spruce wood", "minecraft:spruce_log");

        BLOCK_ALIASES.put("jungle", "minecraft:jungle_log");
        BLOCK_ALIASES.put("jungle log", "minecraft:jungle_log");
        BLOCK_ALIASES.put("jungle logs", "minecraft:jungle_log");
        BLOCK_ALIASES.put("jungle_log", "minecraft:jungle_log");
        BLOCK_ALIASES.put("jungle wood", "minecraft:jungle_log");

        BLOCK_ALIASES.put("acacia", "minecraft:acacia_log");
        BLOCK_ALIASES.put("acacia log", "minecraft:acacia_log");
        BLOCK_ALIASES.put("acacia logs", "minecraft:acacia_log");
        BLOCK_ALIASES.put("acacia_log", "minecraft:acacia_log");
        BLOCK_ALIASES.put("acacia wood", "minecraft:acacia_log");

        BLOCK_ALIASES.put("dark oak", "minecraft:dark_oak_log");
        BLOCK_ALIASES.put("dark oak log", "minecraft:dark_oak_log");
        BLOCK_ALIASES.put("dark oak logs", "minecraft:dark_oak_log");
        BLOCK_ALIASES.put("dark_oak_log", "minecraft:dark_oak_log");
        BLOCK_ALIASES.put("dark oak wood", "minecraft:dark_oak_log");

        BLOCK_ALIASES.put("mangrove", "minecraft:mangrove_log");
        BLOCK_ALIASES.put("mangrove log", "minecraft:mangrove_log");
        BLOCK_ALIASES.put("mangrove logs", "minecraft:mangrove_log");
        BLOCK_ALIASES.put("mangrove_log", "minecraft:mangrove_log");

        BLOCK_ALIASES.put("cherry", "minecraft:cherry_log");
        BLOCK_ALIASES.put("cherry log", "minecraft:cherry_log");
        BLOCK_ALIASES.put("cherry logs", "minecraft:cherry_log");
        BLOCK_ALIASES.put("cherry_log", "minecraft:cherry_log");

        // Generic wood/logs
        BLOCK_ALIASES.put("wood", "minecraft:oak_log");
        BLOCK_ALIASES.put("log", "minecraft:oak_log");
        BLOCK_ALIASES.put("logs", "minecraft:oak_log");
        BLOCK_ALIASES.put("tree", "minecraft:oak_log");
        BLOCK_ALIASES.put("trees", "minecraft:oak_log");

        // Stone variants
        BLOCK_ALIASES.put("stone", "minecraft:stone");
        BLOCK_ALIASES.put("cobblestone", "minecraft:cobblestone");
        BLOCK_ALIASES.put("cobble", "minecraft:cobblestone");
        BLOCK_ALIASES.put("deepslate", "minecraft:deepslate");

        // Deepslate ores
        BLOCK_ALIASES.put("deepslate diamond", "minecraft:deepslate_diamond_ore");
        BLOCK_ALIASES.put("deepslate_diamond_ore", "minecraft:deepslate_diamond_ore");
        BLOCK_ALIASES.put("deepslate iron", "minecraft:deepslate_iron_ore");
        BLOCK_ALIASES.put("deepslate_iron_ore", "minecraft:deepslate_iron_ore");
        BLOCK_ALIASES.put("deepslate gold", "minecraft:deepslate_gold_ore");
        BLOCK_ALIASES.put("deepslate_gold_ore", "minecraft:deepslate_gold_ore");

        // Nether ores
        BLOCK_ALIASES.put("nether gold", "minecraft:nether_gold_ore");
        BLOCK_ALIASES.put("nether_gold_ore", "minecraft:nether_gold_ore");
        BLOCK_ALIASES.put("nether quartz", "minecraft:nether_quartz_ore");
        BLOCK_ALIASES.put("quartz", "minecraft:nether_quartz_ore");
        BLOCK_ALIASES.put("quartz ore", "minecraft:nether_quartz_ore");

        // Crops
        BLOCK_ALIASES.put("wheat", "minecraft:wheat");
        BLOCK_ALIASES.put("carrots", "minecraft:carrots");
        BLOCK_ALIASES.put("carrot", "minecraft:carrots");
        BLOCK_ALIASES.put("potatoes", "minecraft:potatoes");
        BLOCK_ALIASES.put("potato", "minecraft:potatoes");
        BLOCK_ALIASES.put("beetroot", "minecraft:beetroots");
        BLOCK_ALIASES.put("beetroots", "minecraft:beetroots");

        // Other useful blocks
        BLOCK_ALIASES.put("sand", "minecraft:sand");
        BLOCK_ALIASES.put("gravel", "minecraft:gravel");
        BLOCK_ALIASES.put("clay", "minecraft:clay");
        BLOCK_ALIASES.put("obsidian", "minecraft:obsidian");
        BLOCK_ALIASES.put("dirt", "minecraft:dirt");
        BLOCK_ALIASES.put("grass", "minecraft:grass_block");
    }

    // Patterns for parsing commands
    private static final Pattern GOTO_COORDS_PATTERN = Pattern.compile(
            "^(?:go\\s*(?:to)?|goto|walk\\s*(?:to)?|move\\s*(?:to)?|travel\\s*(?:to)?)\\s+" +
                    "(-?\\d+)\\s+(?:(-?\\d+)\\s+)?(-?\\d+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MINE_PATTERN = Pattern.compile(
            "^(?:mine|dig|get|collect|gather|harvest)\\s+(?:(\\d+)\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "^(?:follow|chase|track)\\s+(?:player\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GOTO_BLOCK_PATTERN = Pattern.compile(
            "^(?:go\\s*(?:to)?|goto|find|locate)\\s+(?:nearest\\s+)?(.+)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * Result of routing attempt.
     */
    public static class RouterResult {
        public final boolean handled;
        public final LLMResponse response;
        public final String message;

        private RouterResult(boolean handled, LLMResponse response, String message) {
            this.handled = handled;
            this.response = response;
            this.message = message;
        }

        public static RouterResult notHandled() {
            return new RouterResult(false, null, null);
        }

        public static RouterResult handled(LLMResponse response, String message) {
            return new RouterResult(true, response, message);
        }
    }

    /**
     * Try to handle a command via fast path (no LLM).
     * Returns RouterResult with handled=true if command was routed, false otherwise.
     */
    public RouterResult tryRoute(String instruction) {
        String normalized = instruction.trim().toLowerCase();

        // Stop/pause/resume - immediate execution
        if (normalized.equals("stop") || normalized.equals("cancel")) {
            return RouterResult.handled(createStopResponse(), "Router: stop");
        }
        if (normalized.equals("pause")) {
            return RouterResult.handled(createStopResponse(), "Router: pause");
        }
        if (normalized.equals("resume") || normalized.equals("continue")) {
            // Resume doesn't have a direct Baritone equivalent - just acknowledge
            return RouterResult.handled(createEmptyResponse("Resuming..."), "Router: resume");
        }

        // Come here - goto player's current position
        if (normalized.equals("come here") || normalized.equals("come") ||
                normalized.equals("come to me") || normalized.equals("here")) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player != null) {
                int x = (int) player.getX();
                int y = (int) player.getY();
                int z = (int) player.getZ();
                return RouterResult.handled(
                        createGotoResponse(x, y, z),
                        "Router: come here -> goto " + x + " " + y + " " + z);
            }
        }

        // Try goto coordinates: "goto 100 64 -200" or "goto 100 -200"
        Matcher gotoMatcher = GOTO_COORDS_PATTERN.matcher(normalized);
        if (gotoMatcher.matches()) {
            int x = Integer.parseInt(gotoMatcher.group(1));
            String yStr = gotoMatcher.group(2);
            int z = Integer.parseInt(gotoMatcher.group(3));

            Integer y = null;
            if (yStr != null) {
                y = Integer.parseInt(yStr);
            }

            return RouterResult.handled(
                    createGotoResponse(x, y, z),
                    "Router: goto " + x + (y != null ? " " + y : "") + " " + z);
        }

        // Try mine: "mine 10 diamonds" or "mine diamond ore"
        Matcher mineMatcher = MINE_PATTERN.matcher(normalized);
        if (mineMatcher.matches()) {
            String countStr = mineMatcher.group(1);
            String blockName = mineMatcher.group(2).trim();

            int count = countStr != null ? Integer.parseInt(countStr) : 16; // Default to 16
            String blockId = resolveBlockId(blockName);

            if (blockId != null) {
                return RouterResult.handled(
                        createMineResponse(blockId, count),
                        "Router: mine " + count + "x " + blockId);
            }
        }

        // Try follow: "follow Steve" or "follow player Steve"
        Matcher followMatcher = FOLLOW_PATTERN.matcher(normalized);
        if (followMatcher.matches()) {
            String target = followMatcher.group(1).trim();
            return RouterResult.handled(
                    createFollowResponse(target),
                    "Router: follow " + target);
        }

        // Try goto block: "goto diamond ore" or "find crafting table"
        Matcher gotoBlockMatcher = GOTO_BLOCK_PATTERN.matcher(normalized);
        if (gotoBlockMatcher.matches()) {
            String blockName = gotoBlockMatcher.group(1).trim();
            String blockId = resolveBlockId(blockName);

            if (blockId != null) {
                return RouterResult.handled(
                        createGotoBlockResponse(blockId),
                        "Router: goto " + blockId);
            }
        }

        // Farm command
        if (normalized.startsWith("farm")) {
            String rest = normalized.substring(4).trim();
            int range = 0;
            if (!rest.isEmpty()) {
                try {
                    range = Integer.parseInt(rest);
                } catch (NumberFormatException ignored) {
                }
            }
            return RouterResult.handled(
                    createFarmResponse(range),
                    "Router: farm" + (range > 0 ? " " + range : ""));
        }

        // Explore command
        if (normalized.startsWith("explore")) {
            return RouterResult.handled(
                    createExploreResponse(),
                    "Router: explore");
        }

        // Not handled - needs LLM
        return RouterResult.notHandled();
    }

    /**
     * Resolve a block name to a minecraft block ID.
     */
    public String resolveBlockId(String blockName) {
        String normalized = blockName.toLowerCase().trim();

        // Check aliases first
        if (BLOCK_ALIASES.containsKey(normalized)) {
            return BLOCK_ALIASES.get(normalized);
        }

        // If it already has a namespace, use it
        if (normalized.contains(":")) {
            return normalized;
        }

        // Try adding minecraft namespace
        String withNamespace = "minecraft:" + normalized.replace(" ", "_");
        return withNamespace;
    }

    // Response builders

    private LLMResponse createStopResponse() {
        return createSingleActionResponse("stop", null, null, null, null, null, "Stopping all actions.");
    }

    private LLMResponse createEmptyResponse(String summary) {
        LLMResponse response = new LLMResponse();
        response.setActions(new ArrayList<>());
        response.setChatSummary(summary);
        return response;
    }

    private LLMResponse createGotoResponse(int x, Integer y, int z) {
        String summary = y != null ?
                "Going to " + x + ", " + y + ", " + z :
                "Going to X=" + x + " Z=" + z;
        return createSingleActionResponse("goto", x, y, z, null, null, summary);
    }

    private LLMResponse createGotoBlockResponse(String blockId) {
        return createSingleActionResponse("goto", null, null, null, blockId, null,
                "Going to nearest " + blockId);
    }

    private LLMResponse createMineResponse(String blockId, int count) {
        LLMResponse response = new LLMResponse();
        LLMResponse.Action action = new LLMResponse.Action();
        action.setType("mine");
        action.setBlock(blockId);
        action.setCount(count);
        response.setActions(Collections.singletonList(action));
        response.setChatSummary("Mining " + count + "x " + blockId);
        return response;
    }

    private LLMResponse createFollowResponse(String target) {
        LLMResponse response = new LLMResponse();
        LLMResponse.Action action = new LLMResponse.Action();
        action.setType("follow");
        action.setTarget(target);
        response.setActions(Collections.singletonList(action));
        response.setChatSummary("Following " + target);
        return response;
    }

    private LLMResponse createFarmResponse(int range) {
        LLMResponse response = new LLMResponse();
        LLMResponse.Action action = new LLMResponse.Action();
        action.setType("farm");
        if (range > 0) {
            action.setRange(range);
        }
        response.setActions(Collections.singletonList(action));
        response.setChatSummary(range > 0 ? "Farming within " + range + " blocks" : "Farming nearby crops");
        return response;
    }

    private LLMResponse createExploreResponse() {
        LLMResponse response = new LLMResponse();
        LLMResponse.Action action = new LLMResponse.Action();
        action.setType("explore");
        response.setActions(Collections.singletonList(action));
        response.setChatSummary("Exploring the area");
        return response;
    }

    private LLMResponse createSingleActionResponse(String type, Integer x, Integer y, Integer z,
                                                   String block, String target, String summary) {
        LLMResponse response = new LLMResponse();
        LLMResponse.Action action = new LLMResponse.Action();
        action.setType(type);
        if (x != null) action.setX(x);
        if (y != null) action.setY(y);
        if (z != null) action.setZ(z);
        if (block != null) action.setBlock(block);
        if (target != null) action.setTarget(target);
        response.setActions(Collections.singletonList(action));
        response.setChatSummary(summary);
        return response;
    }
}
