package com.aicommander.util;

import com.aicommander.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class for sending formatted chat messages.
 */
public class ChatUtils {

    private static final String PREFIX = "[AI] ";
    private static final ConcurrentLinkedQueue<Text> messageQueue = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean processingQueue = new AtomicBoolean(false);

    /**
     * Send an info message (white/gray).
     */
    public static void sendInfo(String message) {
        sendMessage(message, Formatting.WHITE);
    }

    /**
     * Send a success message (green).
     */
    public static void sendSuccess(String message) {
        sendMessage(message, Formatting.GREEN);
    }

    /**
     * Send a warning message (yellow).
     */
    public static void sendWarning(String message) {
        sendMessage(message, Formatting.YELLOW);
    }

    /**
     * Send an error message (red).
     */
    public static void sendError(String message) {
        sendMessage(message, Formatting.RED);
    }

    /**
     * Send a debug message (gray, only if debug enabled).
     */
    public static void sendDebug(String message) {
        if (ModConfig.getInstance().showDebugMessages) {
            sendMessage("[DEBUG] " + message, Formatting.GRAY);
        }
    }

    /**
     * Send a question message (aqua).
     */
    public static void sendQuestion(String message) {
        sendMessage("? " + message, Formatting.AQUA);
    }

    /**
     * Send a message - queues messages and processes them with rate limiting.
     */
    private static void sendMessage(String message, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Build the message
        MutableText prefix = Text.literal(PREFIX).formatted(Formatting.GOLD, Formatting.BOLD);
        MutableText content = Text.literal(message).formatted(color);
        Text fullMessage = prefix.append(content);

        // Add to queue
        messageQueue.add(fullMessage);

        // Start processing if not already running
        if (processingQueue.compareAndSet(false, true)) {
            processQueue();
        }
    }

    /**
     * Process queued messages with rate limiting.
     */
    private static void processQueue() {
        MinecraftClient client = MinecraftClient.getInstance();

        client.execute(() -> {
            Text message = messageQueue.poll();
            if (message != null && client.player != null) {
                client.player.sendMessage(message, false);
            }

            // If more messages, schedule next after rate limit
            if (!messageQueue.isEmpty()) {
                long rateLimit = ModConfig.getInstance().chatRateLimitMs;
                // Schedule next message
                new Thread(() -> {
                    try {
                        Thread.sleep(Math.max(50, rateLimit)); // Minimum 50ms between messages
                    } catch (InterruptedException ignored) {}
                    processQueue();
                }).start();
            } else {
                processingQueue.set(false);
            }
        });
    }
    
    /**
     * Send a raw message without prefix.
     */
    public static void sendRaw(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal(message), false);
            }
        });
    }
    
    /**
     * Send a message with custom formatting.
     */
    public static void send(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(text, false);
            }
        });
    }
    
    /**
     * Send a thinking/processing indicator.
     */
    public static void sendThinking() {
        sendMessage("Thinking...", Formatting.GRAY);
    }
    
    /**
     * Format a duration for display.
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return minutes + "m " + seconds + "s";
    }
}
