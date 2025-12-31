package com.aicommander.llm;

import com.google.gson.annotations.SerializedName;

/**
 * Stage A (fast parse) response from the LLM.
 * A small, structured response to determine intent and whether full planning is needed.
 */
public class StageAResponse {

    @SerializedName("intent")
    private String intent;

    @SerializedName("confidence")
    private float confidence;

    @SerializedName("target")
    private Target target;

    @SerializedName("count")
    private Integer count;

    @SerializedName("needs_planning")
    private boolean needsPlanning;

    @SerializedName("reason")
    private String reason;

    // Getters
    public String getIntent() {
        return intent != null ? intent.toLowerCase() : "";
    }

    public float getConfidence() {
        return confidence;
    }

    public Target getTarget() {
        return target;
    }

    public Integer getCount() {
        return count;
    }

    public boolean needsPlanning() {
        return needsPlanning;
    }

    public String getReason() {
        return reason;
    }

    /**
     * Check if this response is high-confidence and doesn't need Stage B.
     */
    public boolean isHighConfidence() {
        return confidence >= 0.75f && !needsPlanning;
    }

    /**
     * Target information for the intent.
     */
    public static class Target {
        @SerializedName("type")
        private String type; // "block", "entity", "location", "player", "item"

        @SerializedName("id")
        private String id; // Block/entity/item ID

        @SerializedName("name")
        private String name; // Player name or display name

        @SerializedName("x")
        private Integer x;

        @SerializedName("y")
        private Integer y;

        @SerializedName("z")
        private Integer z;

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Integer getX() {
            return x;
        }

        public Integer getY() {
            return y;
        }

        public Integer getZ() {
            return z;
        }

        public boolean hasCoordinates() {
            return x != null && z != null;
        }
    }

    @Override
    public String toString() {
        return "StageA{intent='" + intent + "', conf=" + confidence +
                ", needsPlan=" + needsPlanning + "}";
    }
}
