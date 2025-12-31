package com.aicommander.config;

import com.aicommander.llm.ModelPreset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for the AI Baritone Commander mod.
 * Stored as JSON in config/aicommander.json
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("aicommander.json");

    private static ModConfig INSTANCE;

    // Ollama settings
    public String ollamaHost = "127.0.0.1";
    public int ollamaPort = 11434;
    public String ollamaModel = "llama3.2"; // Legacy, kept for compatibility
    public int ollamaTimeoutSeconds = 60;
    public int ollamaMaxRetries = 2;

    // Model preset settings (new two-stage system)
    public String activePreset = "balanced"; // fast, balanced, thinking
    public String stageAModelOverride = null; // Override Stage A model
    public String stageBModelOverride = null; // Override Stage B model

    // Command settings
    public String commandPrefix = "/ai";
    public String altCommandPrefix = "!ai";
    public boolean useAltPrefix = true;

    // Safety settings
    public boolean safeMode = true;
    public int maxStepsPerGoal = 1000;
    public int maxRetriesPerAction = 3;
    public boolean queueMode = false;
    public int maxActionsPerPlan = 8;

    // Chat settings
    public int chatRateLimitMs = 500; // Reduced for faster feedback
    public boolean showDebugMessages = false;
    public boolean dryRunMode = false;

    // Scanning settings (reduced for faster state collection)
    public int nearbyBlockScanRadius = 12;  // Reduced from 32
    public int nearbyEntityScanRadius = 16; // Reduced from 64

    // Cache settings
    public int planCacheTTLSeconds = 45;
    public int planCacheMaxEntries = 20;
    
    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }
    
    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (IOException e) {
                System.err.println("[AICommander] Failed to load config: " + e.getMessage());
            }
        }
        // Create default config
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[AICommander] Failed to save config: " + e.getMessage());
        }
    }
    
    public String getOllamaUrl() {
        return "http://" + ollamaHost + ":" + ollamaPort;
    }

    public void reload() {
        INSTANCE = load();
    }

    /**
     * Get the active model preset.
     */
    public ModelPreset getActivePreset() {
        return ModelPreset.fromName(activePreset);
    }

    /**
     * Set the active preset by name.
     */
    public void setActivePreset(String presetName) {
        this.activePreset = presetName.toLowerCase();
        // Clear overrides when changing preset
        this.stageAModelOverride = null;
        this.stageBModelOverride = null;
        save();
    }

    /**
     * Set Stage A model override.
     */
    public void setStageAModel(String model) {
        this.stageAModelOverride = model;
        save();
    }

    /**
     * Set Stage B model override.
     */
    public void setStageBModel(String model) {
        this.stageBModelOverride = model;
        save();
    }

    /**
     * Get the effective Stage A model (override or preset default).
     */
    public String getEffectiveStageAModel() {
        return stageAModelOverride != null ? stageAModelOverride : getActivePreset().getStageAModel();
    }

    /**
     * Get the effective Stage B model (override or preset default).
     */
    public String getEffectiveStageBModel() {
        return stageBModelOverride != null ? stageBModelOverride : getActivePreset().getStageBModel();
    }
}
