package com.aicommander.cache;

import com.aicommander.llm.LLMResponse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LRU cache for LLM plan outputs to avoid redundant LLM calls.
 * Cache key is based on: normalized command + dimension + tool tier summary + inventory hash
 * TTL is 45 seconds. Max 20 entries with LRU eviction.
 */
public class PlanCache {

    private static final int MAX_ENTRIES = 20;
    private static final long TTL_MS = 45_000; // 45 seconds

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final LinkedList<String> accessOrder = new LinkedList<>();

    /**
     * Cache entry with timestamp and response.
     */
    private static class CacheEntry {
        final LLMResponse response;
        final long timestamp;
        final boolean wasSuccessful;

        CacheEntry(LLMResponse response, boolean wasSuccessful) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
            this.wasSuccessful = wasSuccessful;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TTL_MS;
        }
    }

    /**
     * Generate a cache key for the given instruction and current game state.
     */
    public String generateKey(String instruction) {
        StringBuilder key = new StringBuilder();

        // Normalized instruction (lowercase, trimmed, collapsed whitespace)
        String normalized = instruction.toLowerCase().trim().replaceAll("\\s+", " ");
        key.append(normalized);

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;

        if (player != null && client.world != null) {
            // Add dimension
            key.append("|dim:");
            key.append(client.world.getRegistryKey().getValue().toString());

            // Add tool tier summary
            key.append("|tools:");
            key.append(getToolTierSummary(player));

            // Add simple inventory hash (just count of distinct item types)
            key.append("|inv:");
            key.append(getInventoryHash(player));
        }

        return key.toString();
    }

    /**
     * Get tool tier summary: pickaxe/axe/shovel tier.
     */
    private String getToolTierSummary(ClientPlayerEntity player) {
        String pickaxe = "none";
        String axe = "none";
        String shovel = "none";

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();

            // Check pickaxe
            if (itemId.contains("pickaxe")) {
                pickaxe = upgradeTier(pickaxe, getTierFromItem(itemId));
            }
            // Check axe
            else if (itemId.contains("_axe")) {
                axe = upgradeTier(axe, getTierFromItem(itemId));
            }
            // Check shovel
            else if (itemId.contains("shovel")) {
                shovel = upgradeTier(shovel, getTierFromItem(itemId));
            }
        }

        return pickaxe.charAt(0) + "/" + axe.charAt(0) + "/" + shovel.charAt(0);
    }

    private String getTierFromItem(String itemId) {
        if (itemId.contains("netherite")) return "netherite";
        if (itemId.contains("diamond")) return "diamond";
        if (itemId.contains("iron")) return "iron";
        if (itemId.contains("golden") || itemId.contains("gold")) return "gold";
        if (itemId.contains("stone")) return "stone";
        if (itemId.contains("wooden") || itemId.contains("wood")) return "wood";
        return "none";
    }

    private String upgradeTier(String current, String newTier) {
        int currentRank = tierRank(current);
        int newRank = tierRank(newTier);
        return newRank > currentRank ? newTier : current;
    }

    private int tierRank(String tier) {
        return switch (tier) {
            case "netherite" -> 6;
            case "diamond" -> 5;
            case "iron" -> 4;
            case "gold" -> 3;
            case "stone" -> 2;
            case "wood" -> 1;
            default -> 0;
        };
    }

    /**
     * Simple inventory hash - count of distinct item types and total items.
     */
    private String getInventoryHash(ClientPlayerEntity player) {
        Set<String> distinctItems = new HashSet<>();
        int totalCount = 0;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                distinctItems.add(Registries.ITEM.getId(stack.getItem()).toString());
                totalCount += stack.getCount();
            }
        }

        return distinctItems.size() + ":" + (totalCount / 10); // Round to nearest 10
    }

    /**
     * Try to get a cached response for the instruction.
     * Returns null if not in cache or expired.
     */
    public LLMResponse get(String instruction) {
        String key = generateKey(instruction);

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(key);
            synchronized (accessOrder) {
                accessOrder.remove(key);
            }
            return null;
        }

        // Update access order (LRU)
        synchronized (accessOrder) {
            accessOrder.remove(key);
            accessOrder.addFirst(key);
        }

        return entry.response;
    }

    /**
     * Cache a response for the instruction.
     * Only caches if response is valid and doesn't contain follow-up questions.
     */
    public void put(String instruction, LLMResponse response, boolean wasSuccessful) {
        if (response == null) return;

        // Don't cache if response has follow-up questions
        if (response.getActions() != null) {
            for (LLMResponse.Action action : response.getActions()) {
                if ("ask".equals(action.getType())) {
                    return; // Don't cache plans that need user input
                }
            }
        }

        String key = generateKey(instruction);

        // Evict oldest if at capacity
        synchronized (accessOrder) {
            while (cache.size() >= MAX_ENTRIES && !accessOrder.isEmpty()) {
                String oldest = accessOrder.removeLast();
                cache.remove(oldest);
            }

            cache.put(key, new CacheEntry(response, wasSuccessful));
            accessOrder.remove(key); // Remove if exists
            accessOrder.addFirst(key);
        }
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        cache.clear();
        synchronized (accessOrder) {
            accessOrder.clear();
        }
    }

    /**
     * Get cache stats for debugging.
     */
    public String getStats() {
        int valid = 0;
        int expired = 0;

        for (CacheEntry entry : cache.values()) {
            if (entry.isExpired()) {
                expired++;
            } else {
                valid++;
            }
        }

        return "Cache: " + valid + " valid, " + expired + " expired, " + MAX_ENTRIES + " max";
    }

    /**
     * Check if instruction is in cache (for quick check without full retrieval).
     */
    public boolean contains(String instruction) {
        String key = generateKey(instruction);
        CacheEntry entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }
}
