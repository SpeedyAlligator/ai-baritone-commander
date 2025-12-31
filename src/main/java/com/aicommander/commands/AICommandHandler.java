package com.aicommander.commands;

import com.aicommander.config.ModConfig;
import com.aicommander.execution.ActionExecutor;
import com.aicommander.execution.ExecutionState;
import com.aicommander.llm.*;
import com.aicommander.planning.MinimalWorldStateCollector;
import com.aicommander.router.CommandRouter;
import com.aicommander.util.ChatUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/**
 * Handles AI command input from chat.
 * Integrates with fast-path router, plan cache, and two-stage LLM planner.
 */
public class AICommandHandler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ModConfig config;
    private final OllamaClient ollamaClient;
    private final MinimalWorldStateCollector stateCollector;
    private final TwoStagePlanner planner;
    private final ActionExecutor executor;
    private final CommandRouter router;

    public AICommandHandler() {
        this.config = ModConfig.getInstance();
        this.ollamaClient = new OllamaClient();
        this.stateCollector = new MinimalWorldStateCollector();
        this.planner = new TwoStagePlanner(ollamaClient, stateCollector);
        this.executor = new ActionExecutor();
        this.router = new CommandRouter();

        // Connect state collector to executor's baritone helper
        this.stateCollector.setBaritoneHelper(executor.getBaritone());
    }

    public boolean isAICommand(String message) {
        String trimmed = message.trim();
        return trimmed.startsWith(config.commandPrefix + " ") ||
                trimmed.equals(config.commandPrefix) ||
                (config.useAltPrefix && (trimmed.startsWith(config.altCommandPrefix + " ") ||
                        trimmed.equals(config.altCommandPrefix)));
    }

    public boolean handleMessage(String message) {
        String trimmed = message.trim();
        String instruction = extractInstruction(trimmed);

        if (instruction == null) return false;
        if (instruction.isEmpty()) {
            showUsage();
            return true;
        }
        if (handleSpecialCommand(instruction)) return true;

        handleInstruction(instruction);
        return true;
    }

    private String extractInstruction(String trimmed) {
        if (trimmed.startsWith(config.commandPrefix + " ")) {
            return trimmed.substring(config.commandPrefix.length() + 1).trim();
        } else if (trimmed.equals(config.commandPrefix)) {
            return "";
        } else if (config.useAltPrefix) {
            if (trimmed.startsWith(config.altCommandPrefix + " ")) {
                return trimmed.substring(config.altCommandPrefix.length() + 1).trim();
            } else if (trimmed.equals(config.altCommandPrefix)) {
                return "";
            }
        }
        return null;
    }

    public void showUsage() {
        ChatUtils.sendInfo("Usage: /ai <command>");
        ChatUtils.sendInfo("Commands: do, stop, status, set, models, help");
        ChatUtils.sendInfo("Example: /ai do mine 10 diamonds");
        ChatUtils.sendInfo("Preset: " + config.getActivePreset().getDisplayName());
    }

    private boolean handleSpecialCommand(String instruction) {
        String lower = instruction.toLowerCase().trim();
        String[] parts = lower.split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : "";

        return switch (cmd) {
            case "stop", "cancel" -> {
                handleStop();
                yield true;
            }
            case "pause" -> {
                handlePause();
                yield true;
            }
            case "resume", "continue" -> {
                handleResume();
                yield true;
            }
            case "status" -> {
                handleStatus();
                yield true;
            }
            case "reload" -> {
                handleReload();
                yield true;
            }
            case "help" -> {
                showHelp();
                yield true;
            }
            case "debug" -> {
                toggleDebug();
                yield true;
            }
            case "dryrun" -> {
                toggleDryRun();
                yield true;
            }
            case "safe" -> {
                toggleSafeMode();
                yield true;
            }
            case "set" -> {
                handleSet(arg);
                yield true;
            }
            case "models" -> {
                handleModels();
                yield true;
            }
            case "modela" -> {
                handleModelA(arg);
                yield true;
            }
            case "modelb" -> {
                handleModelB(arg);
                yield true;
            }
            case "cache" -> {
                handleCache(arg);
                yield true;
            }
            case "download" -> {
                handleDownload(arg);
                yield true;
            }
            default -> false;
        };
    }

    /**
     * Handle /ai stop - bulletproof stop that cancels everything.
     */
    public void handleStop() {
        // Cancel Baritone pathing/mining/build
        executor.getBaritone().stop();

        // Clear internal queue and reset state
        executor.cancelAll();

        ChatUtils.sendSuccess("Stopped all AI actions and cleared queue.");
    }

    /**
     * Handle /ai pause - pause current execution.
     */
    public void handlePause() {
        executor.getBaritone().stop();
        ChatUtils.sendInfo("Paused. Use '/ai resume' to continue.");
    }

    /**
     * Handle /ai resume - resume paused execution.
     */
    public void handleResume() {
        // Currently just acknowledges - actual resume would need state tracking
        ChatUtils.sendInfo("Resumed.");
    }

    /**
     * Handle /ai status - show simplified status.
     */
    public void handleStatus() {
        boolean baritoneOk = executor.getBaritone().isAvailable();
        boolean ollamaOk = ollamaClient.isAvailable();

        ChatUtils.sendInfo("=== AI Commander Status ===");
        ChatUtils.sendInfo("Baritone: " + (baritoneOk ? "§aConnected" : "§cNot found"));
        ChatUtils.sendInfo("Ollama: " + (ollamaOk ? "§aConnected" : "§cNot running"));
        ChatUtils.sendInfo("State: " + executor.getState());
        ChatUtils.sendInfo("Preset: " + config.getActivePreset());

        int queueSize = executor.getQueueSize();
        if (queueSize > 0) {
            ChatUtils.sendInfo("Queue: " + queueSize + " action(s) pending");
        }

        if (ollamaClient.isPulling()) {
            ChatUtils.sendInfo("Pulling: " + ollamaClient.getCurrentlyPulling());
        }

        if (!ollamaOk) {
            ChatUtils.sendWarning("Start Ollama with: ollama serve");
        }
    }

    /**
     * Handle /ai reload - reload config.
     */
    public void handleReload() {
        config.reload();
        planner.clearCache();
        ChatUtils.sendSuccess("Config reloaded, cache cleared.");
    }

    /**
     * Handle /ai set <preset> - set model preset.
     */
    public void handleSet(String presetName) {
        if (presetName.isEmpty()) {
            ChatUtils.sendInfo("Current preset: " + config.getActivePreset());
            ChatUtils.sendInfo("Available: fast, balanced, thinking");
            return;
        }

        ModelPreset preset = ModelPreset.fromName(presetName);
        config.setActivePreset(presetName);

        ChatUtils.sendSuccess("Preset set to: " + preset.getDisplayName());
        ChatUtils.sendInfo("Stage A: " + preset.getStageAModel());
        ChatUtils.sendInfo("Stage B: " + preset.getStageBModel());

        // Check if models are installed
        ensureModelsInstalled(preset.getStageAModel(), preset.getStageBModel());
    }

    /**
     * Handle /ai models - list installed models.
     */
    public void handleModels() {
        if (!ollamaClient.isAvailable()) {
            ChatUtils.sendError("Ollama not running!");
            return;
        }

        List<String> models = ollamaClient.getInstalledModels();
        if (models.isEmpty()) {
            ChatUtils.sendWarning("No models installed.");
            ChatUtils.sendInfo("Pull a model with: ollama pull <model>");
            return;
        }

        ChatUtils.sendInfo("=== Installed Models ===");
        for (String model : models) {
            String marker = "";
            if (model.startsWith(config.getEffectiveStageAModel().split(":")[0])) {
                marker = " §a[A]";
            } else if (model.startsWith(config.getEffectiveStageBModel().split(":")[0])) {
                marker = " §b[B]";
            }
            ChatUtils.sendInfo("  " + model + marker);
        }
    }

    /**
     * Handle /ai modelA <name> - set Stage A model override.
     */
    public void handleModelA(String model) {
        if (model.isEmpty()) {
            ChatUtils.sendInfo("Stage A model: " + config.getEffectiveStageAModel());
            return;
        }

        config.setStageAModel(model);
        ChatUtils.sendSuccess("Stage A model set to: " + model);

        if (!ollamaClient.isModelInstalled(model)) {
            pullModelWithProgress(model);
        }
    }

    /**
     * Handle /ai modelB <name> - set Stage B model override.
     */
    public void handleModelB(String model) {
        if (model.isEmpty()) {
            ChatUtils.sendInfo("Stage B model: " + config.getEffectiveStageBModel());
            return;
        }

        config.setStageBModel(model);
        ChatUtils.sendSuccess("Stage B model set to: " + model);

        if (!ollamaClient.isModelInstalled(model)) {
            pullModelWithProgress(model);
        }
    }

    /**
     * Handle /ai cache <clear|stats> - manage plan cache.
     */
    public void handleCache(String arg) {
        if ("clear".equals(arg)) {
            planner.clearCache();
            ChatUtils.sendSuccess("Plan cache cleared.");
        } else {
            ChatUtils.sendInfo(planner.getCacheStats());
        }
    }

    /**
     * Handle /ai download [model] - show recommended models or download one.
     */
    public void handleDownload(String modelName) {
        if (!ollamaClient.isAvailable()) {
            ChatUtils.sendError("Ollama not running! Start with: ollama serve");
            return;
        }

        if (modelName.isEmpty()) {
            showRecommendedModels();
            return;
        }

        // Check if it's a shorthand name
        String fullModelName = resolveModelName(modelName);

        if (ollamaClient.isModelInstalled(fullModelName)) {
            ChatUtils.sendInfo("Model already installed: " + fullModelName);
            return;
        }

        pullModelWithProgress(fullModelName);
    }

    /**
     * Show the list of recommended models with descriptions.
     */
    private void showRecommendedModels() {
        ChatUtils.sendInfo("=== Recommended Models ===");
        ChatUtils.sendInfo("");
        ChatUtils.sendInfo("§6§l#1 qwen2.5:7b§r §7- Best overall (planner + fallback)");
        ChatUtils.sendInfo("   §7Best balance of reasoning, structure, and speed");
        ChatUtils.sendInfo("   §aUse for: Stage B planner, balanced preset");
        ChatUtils.sendInfo("");
        ChatUtils.sendInfo("§6§l#2 phi3.5:mini§r §7- Best fast router/parser");
        ChatUtils.sendInfo("   §7Extremely fast, clean JSON, tiny footprint");
        ChatUtils.sendInfo("   §aUse for: Stage A router, fast preset");
        ChatUtils.sendInfo("");
        ChatUtils.sendInfo("§6§l#3 llama3.2:3b§r §7- Best simple all-round fast model");
        ChatUtils.sendInfo("   §7Reliable, well-tested, good fallback");
        ChatUtils.sendInfo("   §aUse for: Stage A alt, speed-first setup");
        ChatUtils.sendInfo("");
        ChatUtils.sendInfo("§6§l#4 mistral-nemo:12b§r §7- Best thinking mode");
        ChatUtils.sendInfo("   §7Strong reasoning, good for complex tasks");
        ChatUtils.sendInfo("   §aUse for: Thinking preset, hard planning");
        ChatUtils.sendInfo("");
        ChatUtils.sendInfo("§eDownload with: /ai download <model>");
        ChatUtils.sendInfo("§7Shorthand: qwen, phi, llama, mistral");
    }

    /**
     * Resolve shorthand model names to full names.
     */
    private String resolveModelName(String name) {
        return switch (name.toLowerCase()) {
            case "qwen", "qwen2.5", "qwen2.5:7b" -> "qwen2.5:7b";
            case "phi", "phi3", "phi3.5", "phi3.5:mini", "phi3:mini" -> "phi3.5:mini";
            case "llama", "llama3", "llama3.2", "llama3.2:3b" -> "llama3.2:3b";
            case "mistral", "nemo", "mistral-nemo", "mistral-nemo:12b" -> "mistral-nemo:12b-instruct";
            default -> name; // Use as-is if not a shorthand
        };
    }

    /**
     * Ensure models are installed, pull if missing.
     */
    private void ensureModelsInstalled(String stageAModel, String stageBModel) {
        if (!ollamaClient.isModelInstalled(stageAModel)) {
            ChatUtils.sendWarning("Stage A model not installed: " + stageAModel);
            pullModelWithProgress(stageAModel);
        }

        if (!ollamaClient.isModelInstalled(stageBModel)) {
            ChatUtils.sendWarning("Stage B model not installed: " + stageBModel);
            pullModelWithProgress(stageBModel);
        }
    }

    /**
     * Pull a model with progress updates in chat.
     */
    private void pullModelWithProgress(String model) {
        ChatUtils.sendInfo("Pulling model: " + model + "...");

        ollamaClient.pullModel(model, progress -> {
            // Update on main thread
            MinecraftClient.getInstance().execute(() -> {
                if (progress.isComplete()) {
                    ChatUtils.sendSuccess("Model installed: " + model);
                } else if (progress.isError()) {
                    ChatUtils.sendError("Pull failed: " + progress.status);
                } else if (progress.percent >= 0) {
                    // Only show significant progress updates
                    if (progress.percent % 25 == 0 || progress.percent == 100) {
                        ChatUtils.sendInfo("Pulling " + model + ": " + progress.formatProgress());
                    }
                }
            });
        }).exceptionally(e -> {
            MinecraftClient.getInstance().execute(() -> {
                ChatUtils.sendError("Pull failed: " + e.getMessage());
            });
            return false;
        });
    }

    public void toggleDebug() {
        config.showDebugMessages = !config.showDebugMessages;
        config.save();
        ChatUtils.sendInfo("Debug: " + (config.showDebugMessages ? "ON" : "OFF"));
    }

    public void toggleDryRun() {
        config.dryRunMode = !config.dryRunMode;
        config.save();
        ChatUtils.sendInfo("Dry run: " + (config.dryRunMode ? "ON" : "OFF"));
    }

    public void toggleSafeMode() {
        config.safeMode = !config.safeMode;
        config.save();
        ChatUtils.sendInfo("Safe mode: " + (config.safeMode ? "ON" : "OFF"));
    }

    public void handleInstruction(String instruction) {
        if (executor.getState() == ExecutionState.WAITING_INPUT && executor.handleInput(instruction)) {
            return;
        }

        // Cancel previous job if not in queue mode
        if (executor.isBusy() && !config.queueMode) {
            ChatUtils.sendInfo("Cancelling previous task...");
            executor.cancelAll();
        }

        // Try fast-path router first (no LLM)
        CommandRouter.RouterResult routerResult = router.tryRoute(instruction);
        if (routerResult.handled) {
            ChatUtils.sendDebug("FAST PATH: " + routerResult.message);
            if (routerResult.response != null) {
                if (routerResult.response.getChatSummary() != null) {
                    ChatUtils.sendInfo(routerResult.response.getChatSummary());
                }
                if (routerResult.response.getActions() != null &&
                        !routerResult.response.getActions().isEmpty()) {
                    executor.execute(routerResult.response, () -> {
                    }, () -> ChatUtils.sendError("Failed"));
                }
            }
            return;
        }

        // Need LLM - check availability
        if (!ollamaClient.isAvailable()) {
            ChatUtils.sendError("Ollama not running! Start with: ollama serve");
            return;
        }
        if (!executor.getBaritone().isAvailable()) {
            ChatUtils.sendWarning("Baritone not detected.");
        }

        ChatUtils.sendThinking();
        planner.planActions(instruction)
                .thenAccept(response -> {
                    MinecraftClient.getInstance().execute(() -> {
                        if (config.dryRunMode) {
                            ChatUtils.sendInfo("=== DRY RUN ===");
                            ChatUtils.sendRaw(GSON.toJson(response));
                        } else {
                            if (response.getChatSummary() != null) {
                                ChatUtils.sendInfo(response.getChatSummary());
                            }
                            executor.execute(response, () -> {
                            }, () -> {
                                ChatUtils.sendError("Failed");
                                planner.setLastFailureReason("Execution failed");
                            });
                        }
                    });
                })
                .exceptionally(e -> {
                    MinecraftClient.getInstance().execute(() -> {
                        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                        ChatUtils.sendError("Error: " + msg);
                        planner.setLastFailureReason(msg);
                    });
                    return null;
                });
    }

    public void showHelp() {
        ChatUtils.sendInfo("=== AI Commander Help ===");
        ChatUtils.sendInfo("/ai do <instruction> - Execute AI command");
        ChatUtils.sendInfo("/ai stop - Stop all actions");
        ChatUtils.sendInfo("/ai status - Show connection status");
        ChatUtils.sendInfo("/ai set <fast|balanced|thinking> - Set preset");
        ChatUtils.sendInfo("/ai models - List installed models");
        ChatUtils.sendInfo("/ai modelA/modelB <name> - Override models");
        ChatUtils.sendInfo("/ai cache clear|stats - Manage cache");
        ChatUtils.sendInfo("/ai debug - Toggle debug mode");
        ChatUtils.sendInfo("/ai help - Show this help");
    }

    public void shutdown() {
        executor.shutdown();
    }

    public ActionExecutor getExecutor() {
        return executor;
    }

    public TwoStagePlanner getPlanner() {
        return planner;
    }

    public OllamaClient getOllamaClient() {
        return ollamaClient;
    }
}
