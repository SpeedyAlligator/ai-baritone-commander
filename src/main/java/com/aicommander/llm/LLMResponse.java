package com.aicommander.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Represents a response from the LLM containing one or more actions.
 */
public class LLMResponse {
    
    @SerializedName("actions")
    private List<Action> actions;
    
    @SerializedName("reason")
    private String reason;
    
    @SerializedName("chat_summary")
    private String chatSummary;
    
    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getChatSummary() {
        return chatSummary;
    }

    public void setChatSummary(String chatSummary) {
        this.chatSummary = chatSummary;
    }

    public boolean isValid() {
        return actions != null && !actions.isEmpty() && chatSummary != null;
    }
    
    /**
     * Represents a single action the LLM wants to execute.
     */
    public static class Action {
        @SerializedName("type")
        private String type;
        
        // For goto
        @SerializedName("x")
        private Integer x;
        
        @SerializedName("y")
        private Integer y;
        
        @SerializedName("z")
        private Integer z;
        
        // For goto block type
        @SerializedName("block")
        private String block;
        
        // For mine
        @SerializedName("count")
        private Integer count;
        
        // For craft
        @SerializedName("item")
        private String item;
        
        // For explore
        @SerializedName("distance")
        private Integer distance;
        
        // For follow
        @SerializedName("target")
        private String target;
        
        // For ask (clarification)
        @SerializedName("question")
        private String question;
        
        // For farm
        @SerializedName("range")
        private Integer range;
        
        // For build
        @SerializedName("schematic")
        private String schematic;
        
        // For equip
        @SerializedName("slot")
        private Integer slot;
        
        public String getType() {
            return type != null ? type.toLowerCase() : "";
        }

        public void setType(String type) { this.type = type; }

        public Integer getX() { return x; }
        public void setX(Integer x) { this.x = x; }

        public Integer getY() { return y; }
        public void setY(Integer y) { this.y = y; }

        public Integer getZ() { return z; }
        public void setZ(Integer z) { this.z = z; }

        public String getBlock() { return block; }
        public void setBlock(String block) { this.block = block; }

        public Integer getCount() { return count != null ? count : 1; }
        public void setCount(Integer count) { this.count = count; }

        public String getItem() { return item; }
        public void setItem(String item) { this.item = item; }

        public Integer getDistance() { return distance != null ? distance : 100; }
        public void setDistance(Integer distance) { this.distance = distance; }

        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }

        public Integer getRange() { return range; }
        public void setRange(Integer range) { this.range = range; }

        public String getSchematic() { return schematic; }
        public void setSchematic(String schematic) { this.schematic = schematic; }

        public Integer getSlot() { return slot; }
        public void setSlot(Integer slot) { this.slot = slot; }

        public boolean hasCoordinates() {
            return x != null && z != null;
        }

        public boolean hasBlock() {
            return block != null && !block.isEmpty();
        }

        @Override
        public String toString() {
            return "Action{type='" + type + "'}";
        }
    }
}
