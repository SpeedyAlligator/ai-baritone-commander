package com.aicommander.execution;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Helper class for interacting with Baritone API via reflection.
 * This allows the mod to work even if Baritone is not installed (graceful degradation).
 */
public class BaritoneHelper {

    private static final String BARITONE_API_CLASS = "baritone.api.BaritoneAPI";

    private Object baritoneInstance;
    private Class<?> baritoneInterface; // IBaritone interface for method lookups
    private Object settings;
    private boolean baritoneAvailable = false;
    private int initAttempts = 0;
    private static final int MAX_INIT_ATTEMPTS = 3;

    public BaritoneHelper() {
        // Don't initialize here - Baritone may not be ready yet
    }

    /**
     * Initialize the Baritone API connection (lazy initialization).
     * Will retry up to MAX_INIT_ATTEMPTS times if it fails.
     */
    private void initializeBaritone() {
        // If already connected, skip
        if (baritoneAvailable) return;

        // If we've tried too many times, give up
        if (initAttempts >= MAX_INIT_ATTEMPTS) return;

        initAttempts++;
        System.out.println("[AICommander] Attempting to connect to Baritone (attempt " + initAttempts + "/" + MAX_INIT_ATTEMPTS + ")");

        try {
            // First check if the class exists
            Class<?> apiClass = Class.forName(BARITONE_API_CLASS);
            System.out.println("[AICommander] Found BaritoneAPI class");

            // Get the provider
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            System.out.println("[AICommander] Got provider: " + provider);

            if (provider == null) {
                System.err.println("[AICommander] Baritone provider is null - Baritone may not be fully initialized yet");
                return;
            }

            // The runtime class is obfuscated, but we can call methods through the interface
            // Get the interface from the provider's implemented interfaces
            Class<?> providerInterface = null;
            for (Class<?> iface : provider.getClass().getInterfaces()) {
                if (iface.getName().equals("baritone.api.IBaritoneProvider")) {
                    providerInterface = iface;
                    break;
                }
            }

            if (providerInterface == null) {
                System.err.println("[AICommander] Could not find IBaritoneProvider interface. Interfaces:");
                for (Class<?> iface : provider.getClass().getInterfaces()) {
                    System.err.println("  - " + iface.getName());
                }
                return;
            }

            // The interface methods might also be obfuscated - find method that returns IBaritone and takes no params
            System.out.println("[AICommander] IBaritoneProvider methods:");
            Method getPrimaryMethod = null;
            for (Method m : providerInterface.getMethods()) {
                System.out.println("  - " + m.getName() + " returns " + m.getReturnType().getName());
                // Look for a method with no params that returns something with "Baritone" in the name
                if (m.getParameterCount() == 0 && m.getReturnType().getName().contains("Baritone")) {
                    getPrimaryMethod = m;
                    System.out.println("    ^ Found potential getPrimaryBaritone!");
                }
            }

            if (getPrimaryMethod == null) {
                // Try to find any method with 0 params that doesn't return void, List, or common types
                for (Method m : providerInterface.getMethods()) {
                    if (m.getParameterCount() == 0 &&
                        !m.getReturnType().equals(void.class) &&
                        !m.getReturnType().equals(java.util.List.class) &&
                        !m.getReturnType().getName().startsWith("java.")) {
                        getPrimaryMethod = m;
                        System.out.println("[AICommander] Using fallback method: " + m.getName());
                        break;
                    }
                }
            }

            if (getPrimaryMethod == null) {
                System.err.println("[AICommander] Could not find a suitable method to get Baritone instance");
                return;
            }

            baritoneInstance = getPrimaryMethod.invoke(provider);
            System.out.println("[AICommander] Got baritone instance: " + baritoneInstance);

            if (baritoneInstance == null) {
                System.err.println("[AICommander] Baritone instance is null - are you in a world?");
                return;
            }

            // Find the IBaritone interface for method lookups (since runtime class is obfuscated)
            // Must use the same classloader as the instance to avoid "object is not an instance of declaring class"
            ClassLoader instanceLoader = baritoneInstance.getClass().getClassLoader();

            try {
                baritoneInterface = Class.forName("baritone.api.IBaritone", true, instanceLoader);
                System.out.println("[AICommander] Loaded IBaritone interface via instance classloader: " + baritoneInterface.getName());
            } catch (ClassNotFoundException e) {
                System.out.println("[AICommander] Could not load baritone.api.IBaritone, searching interfaces...");

                // Check direct interfaces
                for (Class<?> iface : baritoneInstance.getClass().getInterfaces()) {
                    System.out.println("[AICommander] Found interface: " + iface.getName());
                    if (iface.getName().contains("IBaritone") || iface.getName().contains("Baritone")) {
                        baritoneInterface = iface;
                        System.out.println("[AICommander] Using interface: " + iface.getName());
                        break;
                    }
                }

                // If still not found, check superclass interfaces
                if (baritoneInterface == null) {
                    Class<?> superClass = baritoneInstance.getClass().getSuperclass();
                    while (superClass != null && baritoneInterface == null) {
                        for (Class<?> iface : superClass.getInterfaces()) {
                            System.out.println("[AICommander] Found superclass interface: " + iface.getName());
                            if (iface.getName().contains("IBaritone") || iface.getName().contains("Baritone")) {
                                baritoneInterface = iface;
                                System.out.println("[AICommander] Using superclass interface: " + iface.getName());
                                break;
                            }
                        }
                        superClass = superClass.getSuperclass();
                    }
                }
            }

            settings = apiClass.getMethod("getSettings").invoke(null);
            baritoneAvailable = true;
            System.out.println("[AICommander] Baritone API connected successfully!");
        } catch (ClassNotFoundException e) {
            System.err.println("[AICommander] Baritone mod not installed - class not found: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[AICommander] Failed to connect to Baritone: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reset initialization state to allow retrying.
     */
    public void resetInit() {
        baritoneAvailable = false;
        initAttempts = 0;
        baritoneInstance = null;
        settings = null;
    }

    /**
     * Check if Baritone is available.
     */
    public boolean isAvailable() {
        initializeBaritone();
        return baritoneAvailable;
    }
    
    /**
     * Check if Baritone is currently pathing.
     * Note: This is unreliable with obfuscated Baritone - always returns false.
     * Use Baritone's chat feedback to determine status instead.
     */
    public boolean isPathing() {
        // Can't reliably check pathing status with obfuscated Baritone
        // The chat commands will report their own status
        return false;
    }
    
    /**
     * Stop all Baritone processes.
     */
    public void stop() {
        initializeBaritone();
        if (!baritoneAvailable) return;
        sendBaritoneCommand("stop");
    }

    /**
     * Navigate to specific coordinates.
     */
    public boolean gotoCoordinates(int x, int y, int z) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        // Baritone command: #goto x y z
        return sendBaritoneCommand("goto " + x + " " + y + " " + z);
    }

    /**
     * Navigate to XZ coordinates (any Y level).
     */
    public boolean gotoXZ(int x, int z) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        // Baritone command: #goto x z (2 args = XZ only)
        return sendBaritoneCommand("goto " + x + " " + z);
    }

    /**
     * Navigate to a block type.
     */
    public boolean gotoBlock(String blockId) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        String blockName = blockId.contains(":") ? blockId.split(":")[1] : blockId;
        // Baritone command: #goto <block>
        return sendBaritoneCommand("goto " + blockName);
    }
    
    /**
     * Mine a specific block type.
     */
    public boolean mine(String blockId, int count) {
        initializeBaritone();
        if (!baritoneAvailable) return false;

        // Use Baritone chat command - much simpler and avoids reflection issues
        // Extract block name from full ID (e.g., "minecraft:birch_log" -> "birch_log")
        String blockName = blockId.contains(":") ? blockId.split(":")[1] : blockId;

        // Baritone command: #mine <count> <block>
        String command = count > 0 ? "mine " + count + " " + blockName : "mine " + blockName;
        return sendBaritoneCommand(command);
    }

    /**
     * Send a command to Baritone via chat prefix.
     */
    private boolean sendBaritoneCommand(String command) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) {
            System.err.println("[AICommander] Cannot send Baritone command - not in game");
            return false;
        }

        // Baritone listens for chat messages starting with # (configurable prefix)
        String fullCommand = "#" + command;
        System.out.println("[AICommander] Sending Baritone command: " + fullCommand);

        client.execute(() -> {
            if (client.player != null) {
                client.player.networkHandler.sendChatMessage(fullCommand);
            }
        });

        return true;
    }

    /**
     * Explore from current position.
     */
    public boolean explore(int x, int z) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        // Baritone command: #explore x z
        return sendBaritoneCommand("explore " + x + " " + z);
    }

    /**
     * Farm crops in the area.
     */
    public boolean farm(int range) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        // Baritone command: #farm or #farm <range>
        if (range > 0) {
            return sendBaritoneCommand("farm " + range);
        }
        return sendBaritoneCommand("farm");
    }

    /**
     * Follow an entity matching the predicate.
     * Note: Chat commands only support following players by name.
     */
    @SuppressWarnings("unchecked")
    public boolean follow(Predicate<Entity> predicate) {
        // For predicate-based follow, we can't use chat commands directly
        // This is a limitation - use followPlayer or followEntityType instead
        System.err.println("[AICommander] Predicate-based follow not supported via chat commands");
        return false;
    }

    /**
     * Follow a player by name.
     */
    public boolean followPlayer(String playerName) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        // Baritone command: #follow player <name>
        return sendBaritoneCommand("follow player " + playerName);
    }

    /**
     * Follow entities of a specific type.
     */
    public boolean followEntityType(String entityTypeId) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        String entityName = entityTypeId.contains(":") ? entityTypeId.split(":")[1] : entityTypeId;
        // Baritone command: #follow entity <type>
        return sendBaritoneCommand("follow " + entityName);
    }
    
    /**
     * Set a Baritone setting.
     */
    public void setSetting(String settingName, Object value) {
        initializeBaritone();
        if (!baritoneAvailable || settings == null) return;
        try {
            Object setting = settings.getClass().getField(settingName).get(settings);
            setting.getClass().getField("value").set(setting, value);
        } catch (Exception e) {
            System.err.println("[AICommander] Failed to set setting " + settingName + ": " + e.getMessage());
        }
    }
    
    /**
     * Get a Baritone setting value.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getSetting(String settingName) {
        initializeBaritone();
        if (!baritoneAvailable || settings == null) return Optional.empty();
        try {
            Object setting = settings.getClass().getField(settingName).get(settings);
            T value = (T) setting.getClass().getField("value").get(setting);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    /**
     * Execute a Baritone command string.
     * This is a fallback for actions not directly supported via API.
     */
    public boolean executeCommand(String command) {
        initializeBaritone();
        if (!baritoneAvailable) return false;
        // Just forward to chat command
        return sendBaritoneCommand(command);
    }
}
