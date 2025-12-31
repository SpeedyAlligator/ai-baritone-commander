package com.aicommander.llm;

/**
 * Model presets for two-stage LLM planning.
 * Each preset defines a Stage A (fast parse) and Stage B (full planner) model.
 */
public enum ModelPreset {
    /**
     * Fast preset - prioritizes speed over accuracy.
     * Good for simple, common commands.
     */
    FAST("phi3:mini", "qwen2.5:7b", 0.2f, 0.3f),

    /**
     * Balanced preset (default) - good balance of speed and accuracy.
     */
    BALANCED("llama3.2:3b", "qwen2.5:7b", 0.3f, 0.3f),

    /**
     * Thinking preset - prioritizes accuracy and complex reasoning.
     * Use for ambiguous or multi-step commands.
     */
    THINKING("llama3.2:3b", "mistral-nemo:12b-instruct", 0.4f, 0.4f);

    private final String stageAModel;
    private final String stageBModel;
    private final float stageATemperature;
    private final float stageBTemperature;

    ModelPreset(String stageAModel, String stageBModel, float stageATemp, float stageBTemp) {
        this.stageAModel = stageAModel;
        this.stageBModel = stageBModel;
        this.stageATemperature = stageATemp;
        this.stageBTemperature = stageBTemp;
    }

    public String getStageAModel() {
        return stageAModel;
    }

    public String getStageBModel() {
        return stageBModel;
    }

    public float getStageATemperature() {
        return stageATemperature;
    }

    public float getStageBTemperature() {
        return stageBTemperature;
    }

    /**
     * Get preset by name (case-insensitive).
     */
    public static ModelPreset fromName(String name) {
        if (name == null) return BALANCED;
        return switch (name.toLowerCase().trim()) {
            case "fast" -> FAST;
            case "thinking" -> THINKING;
            default -> BALANCED;
        };
    }

    /**
     * Get display name.
     */
    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }

    @Override
    public String toString() {
        return getDisplayName() + " [A:" + stageAModel + ", B:" + stageBModel + "]";
    }
}
