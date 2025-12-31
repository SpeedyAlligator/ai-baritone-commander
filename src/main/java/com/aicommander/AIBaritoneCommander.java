package com.aicommander;

import com.aicommander.commands.AICommandHandler;
import com.aicommander.config.ModConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for the AI Baritone Commander mod.
 * 
 * This mod allows you to control Baritone using natural language commands
 * processed by a local LLM via Ollama.
 * 
 * Usage: /ai <instruction> or !ai <instruction>
 * Examples:
 *   /ai mine 10 diamonds
 *   /ai go to the nearest village
 *   /ai follow the player named Steve
 *   /ai stop
 */
public class AIBaritoneCommander implements ClientModInitializer {
    
    public static final String MOD_ID = "aicommander";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    private static AIBaritoneCommander INSTANCE;
    private AICommandHandler commandHandler;
    
    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("AI Baritone Commander initializing...");
        
        // Load configuration
        ModConfig config = ModConfig.getInstance();
        LOGGER.info("Config loaded. Ollama: {}:{}, Model: {}", 
                config.ollamaHost, config.ollamaPort, config.ollamaModel);
        
        // Initialize command handler
        commandHandler = new AICommandHandler();

        // Register the /ai command properly
        registerCommand();

        // Register chat event listener for !ai prefix
        registerChatListener();
        
        // Register shutdown hook
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("AI Baritone Commander shutting down...");
            if (commandHandler != null) {
                commandHandler.shutdown();
            }
        });
        
        LOGGER.info("AI Baritone Commander initialized!");
        LOGGER.info("Use {} or {} to send commands", config.commandPrefix, config.altCommandPrefix);
    }
    
    /**
     * Register the /ai command with Brigadier subcommands.
     */
    private void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ai")
                .executes(context -> {
                    // No arguments - show usage
                    commandHandler.showUsage();
                    return 1;
                })
                // /ai status - Show status
                .then(ClientCommandManager.literal("status")
                    .executes(context -> {
                        commandHandler.handleStatus();
                        return 1;
                    })
                )
                // /ai stop - Stop everything
                .then(ClientCommandManager.literal("stop")
                    .executes(context -> {
                        commandHandler.handleStop();
                        return 1;
                    })
                )
                // /ai help - Show help
                .then(ClientCommandManager.literal("help")
                    .executes(context -> {
                        commandHandler.showHelp();
                        return 1;
                    })
                )
                // /ai reload - Reload config
                .then(ClientCommandManager.literal("reload")
                    .executes(context -> {
                        commandHandler.handleReload();
                        return 1;
                    })
                )
                // /ai debug - Toggle debug mode
                .then(ClientCommandManager.literal("debug")
                    .executes(context -> {
                        commandHandler.toggleDebug();
                        return 1;
                    })
                )
                // /ai pause - Pause execution
                .then(ClientCommandManager.literal("pause")
                    .executes(context -> {
                        commandHandler.handlePause();
                        return 1;
                    })
                )
                // /ai resume - Resume execution
                .then(ClientCommandManager.literal("resume")
                    .executes(context -> {
                        commandHandler.handleResume();
                        return 1;
                    })
                )
                // /ai set [preset] - Set or show model preset
                .then(ClientCommandManager.literal("set")
                    .executes(context -> {
                        commandHandler.handleSet("");
                        return 1;
                    })
                    .then(ClientCommandManager.literal("fast")
                        .executes(context -> {
                            commandHandler.handleSet("fast");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("balanced")
                        .executes(context -> {
                            commandHandler.handleSet("balanced");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("thinking")
                        .executes(context -> {
                            commandHandler.handleSet("thinking");
                            return 1;
                        })
                    )
                )
                // /ai models - List installed models
                .then(ClientCommandManager.literal("models")
                    .executes(context -> {
                        commandHandler.handleModels();
                        return 1;
                    })
                )
                // /ai modelA [name] - Set or show Stage A model
                .then(ClientCommandManager.literal("modelA")
                    .executes(context -> {
                        commandHandler.handleModelA("");
                        return 1;
                    })
                    .then(ClientCommandManager.argument("model", StringArgumentType.greedyString())
                        .executes(context -> {
                            String model = StringArgumentType.getString(context, "model");
                            commandHandler.handleModelA(model);
                            return 1;
                        })
                    )
                )
                // /ai modelB [name] - Set or show Stage B model
                .then(ClientCommandManager.literal("modelB")
                    .executes(context -> {
                        commandHandler.handleModelB("");
                        return 1;
                    })
                    .then(ClientCommandManager.argument("model", StringArgumentType.greedyString())
                        .executes(context -> {
                            String model = StringArgumentType.getString(context, "model");
                            commandHandler.handleModelB(model);
                            return 1;
                        })
                    )
                )
                // /ai cache [clear|stats] - Manage plan cache
                .then(ClientCommandManager.literal("cache")
                    .executes(context -> {
                        commandHandler.handleCache("");
                        return 1;
                    })
                    .then(ClientCommandManager.literal("clear")
                        .executes(context -> {
                            commandHandler.handleCache("clear");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("stats")
                        .executes(context -> {
                            commandHandler.handleCache("stats");
                            return 1;
                        })
                    )
                )
                // /ai dryrun - Toggle dry run mode
                .then(ClientCommandManager.literal("dryrun")
                    .executes(context -> {
                        commandHandler.toggleDryRun();
                        return 1;
                    })
                )
                // /ai safe - Toggle safe mode
                .then(ClientCommandManager.literal("safe")
                    .executes(context -> {
                        commandHandler.toggleSafeMode();
                        return 1;
                    })
                )
                // /ai download [model] - Download recommended models
                .then(ClientCommandManager.literal("download")
                    .executes(context -> {
                        commandHandler.handleDownload("");
                        return 1;
                    })
                    .then(ClientCommandManager.literal("qwen")
                        .executes(context -> {
                            commandHandler.handleDownload("qwen");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("phi")
                        .executes(context -> {
                            commandHandler.handleDownload("phi");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("llama")
                        .executes(context -> {
                            commandHandler.handleDownload("llama");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.literal("mistral")
                        .executes(context -> {
                            commandHandler.handleDownload("mistral");
                            return 1;
                        })
                    )
                    .then(ClientCommandManager.argument("model", StringArgumentType.greedyString())
                        .executes(context -> {
                            String model = StringArgumentType.getString(context, "model");
                            commandHandler.handleDownload(model);
                            return 1;
                        })
                    )
                )
                // /ai do <instruction> - Execute an AI instruction
                .then(ClientCommandManager.literal("do")
                    .then(ClientCommandManager.argument("instruction", StringArgumentType.greedyString())
                        .executes(context -> {
                            String instruction = StringArgumentType.getString(context, "instruction");
                            commandHandler.handleInstruction(instruction);
                            return 1;
                        })
                    )
                )
            );
        });
    }

    /**
     * Register the chat message listener for !ai prefix.
     * The /ai command is handled by Brigadier (registerCommand).
     */
    private void registerChatListener() {
        // Intercept outgoing chat messages for !ai prefix
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            String trimmed = message.trim();
            ModConfig config = ModConfig.getInstance();
            // Only handle !ai prefix here (not /ai, which is handled by Brigadier)
            if (config.useAltPrefix &&
                (trimmed.startsWith(config.altCommandPrefix + " ") || trimmed.equals(config.altCommandPrefix))) {
                commandHandler.handleMessage(message);
                return false; // Don't send to server
            }
            return true; // Send normally
        });
    }
    
    /**
     * Get the mod instance.
     */
    public static AIBaritoneCommander getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get the command handler.
     */
    public AICommandHandler getCommandHandler() {
        return commandHandler;
    }
}
