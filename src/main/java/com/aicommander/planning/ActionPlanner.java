package com.aicommander.planning;

import com.aicommander.config.ModConfig;
import com.aicommander.llm.LLMClient;
import com.aicommander.llm.LLMResponse;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/**
 * Builds prompts and gets action plans from the LLM.
 */
public class ActionPlanner {
    
    private final LLMClient llmClient;
    private final WorldStateCollector stateCollector;
    private final ModConfig config;
    
    public ActionPlanner(LLMClient llmClient, WorldStateCollector stateCollector) {
        this.llmClient = llmClient;
        this.stateCollector = stateCollector;
        this.config = ModConfig.getInstance();
    }
    
    /**
     * Build a prompt and get an action plan from the LLM.
     */
    public CompletableFuture<LLMResponse> planActions(String instruction) {
        String prompt = buildPrompt(instruction);
        return llmClient.sendPrompt(prompt);
    }
    
    /**
     * Build the complete prompt for the LLM.
     */
    private String buildPrompt(String instruction) {
        JsonObject worldState = stateCollector.collectState();
        
        StringBuilder prompt = new StringBuilder();
        
        // System context
        prompt.append("You are an AI assistant controlling a Minecraft player through Baritone.\n");
        prompt.append("You must respond with a valid JSON object containing actions to execute.\n");
        prompt.append("Do NOT include any explanation outside the JSON - ONLY output the JSON object.\n\n");
        
        // Safe mode warning
        if (config.safeMode) {
            prompt.append("SAFE MODE IS ENABLED: Avoid dangerous actions that could kill the player.\n");
            prompt.append("Consider health levels and nearby hostile mobs before acting.\n\n");
        }
        
        // Action schema
        prompt.append("=== ALLOWED ACTIONS ===\n");
        prompt.append(ActionSchema.getPromptSchema());
        prompt.append("\n\n");
        
        // Current world state
        prompt.append("=== CURRENT WORLD STATE ===\n");
        prompt.append(formatWorldState(worldState));
        prompt.append("\n\n");
        
        // The instruction
        prompt.append("=== PLAYER INSTRUCTION ===\n");
        prompt.append(instruction);
        prompt.append("\n\n");
        
        // Response format reminder
        prompt.append("=== YOUR RESPONSE ===\n");
        prompt.append("Respond with ONLY a JSON object in this exact format:\n");
        prompt.append("{\n");
        prompt.append("  \"actions\": [{\"type\": \"...\", ...}],\n");
        prompt.append("  \"reason\": \"brief explanation\",\n");
        prompt.append("  \"chat_summary\": \"message for player\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    /**
     * Format the world state for the prompt (more readable than raw JSON).
     */
    private String formatWorldState(JsonObject state) {
        StringBuilder sb = new StringBuilder();
        
        // Position
        if (state.has("position")) {
            JsonObject pos = state.getAsJsonObject("position");
            sb.append("Position: ").append(pos.get("x").getAsInt())
                    .append(", ").append(pos.get("y").getAsInt())
                    .append(", ").append(pos.get("z").getAsInt()).append("\n");
        }
        
        // Dimension
        if (state.has("dimension")) {
            sb.append("Dimension: ").append(state.get("dimension").getAsString()).append("\n");
        }
        
        // Health and food
        if (state.has("health")) {
            sb.append("Health: ").append(state.get("health").getAsFloat())
                    .append("/").append(state.get("max_health").getAsFloat()).append("\n");
        }
        if (state.has("food")) {
            sb.append("Food: ").append(state.get("food").getAsInt()).append("/20\n");
        }
        
        // Time
        if (state.has("is_day")) {
            sb.append("Time: ").append(state.get("is_day").getAsBoolean() ? "Day" : "Night").append("\n");
        }
        
        // Weather
        if (state.has("is_raining") && state.get("is_raining").getAsBoolean()) {
            sb.append("Weather: Raining");
            if (state.has("is_thundering") && state.get("is_thundering").getAsBoolean()) {
                sb.append(" (Thunder)");
            }
            sb.append("\n");
        }
        
        // Held item
        if (state.has("held_item")) {
            sb.append("Held Item: ").append(state.get("held_item").getAsString());
            if (state.has("held_item_count")) {
                sb.append(" x").append(state.get("held_item_count").getAsInt());
            }
            sb.append("\n");
        }
        
        // Baritone status
        if (state.has("baritone_active")) {
            sb.append("Baritone Active: ").append(state.get("baritone_active").getAsBoolean()).append("\n");
        }
        
        // Inventory (summarized)
        if (state.has("inventory")) {
            sb.append("\nInventory:\n");
            JsonObject inventory = state.getAsJsonObject("inventory");
            int count = 0;
            for (String key : inventory.keySet()) {
                if (count++ >= 20) {
                    sb.append("  ... and more\n");
                    break;
                }
                sb.append("  ").append(key).append(": ")
                        .append(inventory.get(key).getAsInt()).append("\n");
            }
        }
        
        // Nearby blocks
        if (state.has("nearby_blocks")) {
            sb.append("\nNearby Interesting Blocks:\n");
            JsonObject blocks = state.getAsJsonObject("nearby_blocks");
            int count = 0;
            for (String key : blocks.keySet()) {
                if (count++ >= 15) {
                    sb.append("  ... and more\n");
                    break;
                }
                JsonObject blockInfo = blocks.getAsJsonObject(key);
                sb.append("  ").append(key).append(": ")
                        .append(blockInfo.get("count").getAsInt()).append(" nearby\n");
            }
        }
        
        // Nearby entities
        if (state.has("nearby_entities")) {
            sb.append("\nNearby Entities:\n");
            state.getAsJsonArray("nearby_entities").forEach(elem -> {
                JsonObject entity = elem.getAsJsonObject();
                sb.append("  ").append(entity.get("type").getAsString())
                        .append(": ").append(entity.get("count").getAsInt());
                if (entity.has("category")) {
                    sb.append(" (").append(entity.get("category").getAsString()).append(")");
                }
                if (entity.has("nearest")) {
                    JsonObject nearest = entity.getAsJsonObject("nearest");
                    sb.append(" - nearest at distance ").append(nearest.get("distance").getAsInt());
                    if (nearest.has("name")) {
                        sb.append(" (").append(nearest.get("name").getAsString()).append(")");
                    }
                }
                sb.append("\n");
            });
        }
        
        return sb.toString();
    }
    
    /**
     * Check if Ollama is available.
     */
    public boolean isLLMAvailable() {
        return llmClient.isOllamaAvailable();
    }
    
    /**
     * Get the model name being used.
     */
    public String getModelName() {
        return llmClient.getModelName();
    }
}
