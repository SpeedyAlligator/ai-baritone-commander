package com.aicommander.planning;

import com.aicommander.execution.BaritoneHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Collects minimal world state for compact LLM prompts.
 * Designed to keep prompt under ~500 tokens while providing essential context.
 *
 * Includes:
 * - Dimension, position (rounded), yaw/pitch
 * - Health, hunger
 * - Top 12 inventory items + hotbar
 * - Tool tiers (pickaxe/axe/shovel)
 * - Top 8 nearby entities (players > hostile > passive)
 * - Top 12 nearby interesting blocks (ores, logs, crafting stations)
 * - Baritone status
 * - Last failure reason (if any)
 */
public class MinimalWorldStateCollector {

    private static final int MAX_INVENTORY_ITEMS = 12;
    private static final int MAX_ENTITIES = 8;
    private static final int MAX_BLOCKS = 12;
    private static final int ENTITY_RADIUS = 16;
    private static final int BLOCK_RADIUS = 12;

    // Interesting blocks to track
    private static final Set<String> INTERESTING_BLOCKS = Set.of(
            // Ores
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris",
            // Logs
            "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log",
            "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
            "minecraft:mangrove_log", "minecraft:cherry_log", "minecraft:crimson_stem",
            "minecraft:warped_stem",
            // Crafting stations
            "minecraft:crafting_table", "minecraft:furnace", "minecraft:blast_furnace",
            "minecraft:smoker", "minecraft:smithing_table", "minecraft:anvil",
            "minecraft:enchanting_table", "minecraft:brewing_stand", "minecraft:grindstone",
            "minecraft:stonecutter", "minecraft:loom", "minecraft:cartography_table",
            // Storage
            "minecraft:chest", "minecraft:barrel", "minecraft:ender_chest",
            "minecraft:shulker_box",
            // Other useful
            "minecraft:bed", "minecraft:spawner", "minecraft:obsidian",
            "minecraft:water", "minecraft:lava"
    );

    private String lastFailureReason = null;
    private BaritoneHelper baritoneHelper;

    public MinimalWorldStateCollector() {
    }

    public void setBaritoneHelper(BaritoneHelper helper) {
        this.baritoneHelper = helper;
    }

    public void setLastFailureReason(String reason) {
        this.lastFailureReason = reason;
    }

    public void clearLastFailureReason() {
        this.lastFailureReason = null;
    }

    /**
     * Collect minimal world state as a compact JSON object.
     */
    public JsonObject collectState() {
        JsonObject state = new JsonObject();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            state.addProperty("error", "Not in world");
            return state;
        }

        // Dimension
        state.addProperty("dim", world.getRegistryKey().getValue().getPath());

        // Position (rounded)
        JsonObject pos = new JsonObject();
        pos.addProperty("x", Math.round(player.getX()));
        pos.addProperty("y", Math.round(player.getY()));
        pos.addProperty("z", Math.round(player.getZ()));
        state.add("pos", pos);

        // Facing (rounded to nearest 45 degrees)
        state.addProperty("yaw", Math.round(player.getYaw() / 45) * 45);
        state.addProperty("pitch", Math.round(player.getPitch() / 15) * 15);

        // Health and hunger
        state.addProperty("hp", Math.round(player.getHealth()));
        state.addProperty("food", player.getHungerManager().getFoodLevel());

        // Tool tiers
        state.add("tools", getToolTiers(player));

        // Top inventory items
        state.add("inv", getTopInventoryItems(player));

        // Hotbar items (quick reference)
        state.add("hotbar", getHotbarItems(player));

        // Nearby entities
        state.add("entities", getNearbyEntities(player, world));

        // Nearby interesting blocks
        state.add("blocks", getNearbyBlocks(player, world));

        // Baritone status
        if (baritoneHelper != null) {
            JsonObject baritone = new JsonObject();
            baritone.addProperty("available", baritoneHelper.isAvailable());
            baritone.addProperty("pathing", baritoneHelper.isPathing());
            state.add("baritone", baritone);
        }

        // Last failure
        if (lastFailureReason != null && !lastFailureReason.isEmpty()) {
            state.addProperty("lastError", truncate(lastFailureReason, 100));
        }

        return state;
    }

    /**
     * Collect state as a compact string (for smaller prompts).
     */
    public String collectStateCompact() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;

        if (player == null || world == null) {
            return "Not in world";
        }

        StringBuilder sb = new StringBuilder();

        // Basic info on one line
        sb.append("Dim:").append(world.getRegistryKey().getValue().getPath());
        sb.append(" Pos:").append(Math.round(player.getX())).append(",")
                .append(Math.round(player.getY())).append(",")
                .append(Math.round(player.getZ()));
        sb.append(" HP:").append(Math.round(player.getHealth())).append("/20");
        sb.append(" Food:").append(player.getHungerManager().getFoodLevel()).append("/20\n");

        // Tools
        sb.append("Tools: ").append(getToolTiersCompact(player)).append("\n");

        // Top inventory
        sb.append("Inv: ").append(getTopInventoryCompact(player)).append("\n");

        // Nearby entities
        String entities = getNearbyEntitiesCompact(player, world);
        if (!entities.isEmpty()) {
            sb.append("Near: ").append(entities).append("\n");
        }

        // Nearby blocks
        String blocks = getNearbyBlocksCompact(player, world);
        if (!blocks.isEmpty()) {
            sb.append("Blocks: ").append(blocks).append("\n");
        }

        // Baritone
        if (baritoneHelper != null && baritoneHelper.isAvailable()) {
            sb.append("Baritone: ").append(baritoneHelper.isPathing() ? "pathing" : "idle").append("\n");
        }

        // Last error
        if (lastFailureReason != null) {
            sb.append("LastErr: ").append(truncate(lastFailureReason, 80)).append("\n");
        }

        return sb.toString();
    }

    private JsonObject getToolTiers(ClientPlayerEntity player) {
        JsonObject tools = new JsonObject();
        String pickaxe = "none", axe = "none", shovel = "none", sword = "none";

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            String tier = getTierFromItem(itemId);

            if (itemId.contains("pickaxe")) pickaxe = upgradeTier(pickaxe, tier);
            else if (itemId.contains("_axe")) axe = upgradeTier(axe, tier);
            else if (itemId.contains("shovel")) shovel = upgradeTier(shovel, tier);
            else if (itemId.contains("sword")) sword = upgradeTier(sword, tier);
        }

        tools.addProperty("pick", pickaxe);
        tools.addProperty("axe", axe);
        tools.addProperty("shovel", shovel);
        tools.addProperty("sword", sword);
        return tools;
    }

    private String getToolTiersCompact(ClientPlayerEntity player) {
        String pickaxe = "none", axe = "none", shovel = "none";

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            String tier = getTierFromItem(itemId);

            if (itemId.contains("pickaxe")) pickaxe = upgradeTier(pickaxe, tier);
            else if (itemId.contains("_axe")) axe = upgradeTier(axe, tier);
            else if (itemId.contains("shovel")) shovel = upgradeTier(shovel, tier);
        }

        return "pick:" + abbrevTier(pickaxe) + " axe:" + abbrevTier(axe) + " shovel:" + abbrevTier(shovel);
    }

    private String abbrevTier(String tier) {
        return switch (tier) {
            case "netherite" -> "N";
            case "diamond" -> "D";
            case "iron" -> "I";
            case "gold" -> "G";
            case "stone" -> "S";
            case "wood" -> "W";
            default -> "-";
        };
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

    private JsonArray getTopInventoryItems(ClientPlayerEntity player) {
        Map<String, Integer> items = new HashMap<>();

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).getPath(); // Short name
                items.merge(id, stack.getCount(), Integer::sum);
            }
        }

        // Sort by count and take top N
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(items.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        JsonArray arr = new JsonArray();
        int count = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (count >= MAX_INVENTORY_ITEMS) break;
            arr.add(entry.getKey() + ":" + entry.getValue());
            count++;
        }

        return arr;
    }

    private String getTopInventoryCompact(ClientPlayerEntity player) {
        Map<String, Integer> items = new HashMap<>();

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).getPath();
                items.merge(id, stack.getCount(), Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(items.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (count >= 8) break; // Even more compact
            if (count > 0) sb.append(", ");
            sb.append(entry.getKey()).append("x").append(entry.getValue());
            count++;
        }

        return sb.toString();
    }

    private JsonArray getHotbarItems(ClientPlayerEntity player) {
        JsonArray arr = new JsonArray();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                arr.add(i + ":" + Registries.ITEM.getId(stack.getItem()).getPath());
            }
        }
        return arr;
    }

    private JsonArray getNearbyEntities(ClientPlayerEntity player, ClientWorld world) {
        List<EntityInfo> entities = new ArrayList<>();

        for (Entity entity : world.getEntities()) {
            if (entity == player) continue;

            double dist = entity.distanceTo(player);
            if (dist > ENTITY_RADIUS) continue;

            String type = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
            int priority;
            String category;

            if (entity instanceof PlayerEntity) {
                priority = 0;
                category = "player";
            } else if (entity instanceof HostileEntity) {
                priority = 1;
                category = "hostile";
            } else if (entity instanceof AnimalEntity) {
                priority = 2;
                category = "animal";
            } else if (entity instanceof LivingEntity) {
                priority = 3;
                category = "mob";
            } else {
                continue; // Skip non-living entities
            }

            entities.add(new EntityInfo(type, category, (int) dist, priority));
        }

        // Sort by priority then distance
        entities.sort((a, b) -> {
            if (a.priority != b.priority) return a.priority - b.priority;
            return a.distance - b.distance;
        });

        JsonArray arr = new JsonArray();
        int count = 0;
        for (EntityInfo info : entities) {
            if (count >= MAX_ENTITIES) break;
            JsonObject obj = new JsonObject();
            obj.addProperty("type", info.type);
            obj.addProperty("dist", info.distance);
            obj.addProperty("cat", info.category);
            arr.add(obj);
            count++;
        }

        return arr;
    }

    private String getNearbyEntitiesCompact(ClientPlayerEntity player, ClientWorld world) {
        Map<String, int[]> entities = new HashMap<>(); // type -> [count, minDist]

        for (Entity entity : world.getEntities()) {
            if (entity == player) continue;
            if (!(entity instanceof LivingEntity)) continue;

            double dist = entity.distanceTo(player);
            if (dist > ENTITY_RADIUS) continue;

            String type = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
            int[] data = entities.computeIfAbsent(type, k -> new int[]{0, Integer.MAX_VALUE});
            data[0]++;
            data[1] = Math.min(data[1], (int) dist);
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, int[]> entry : entities.entrySet()) {
            if (count >= 5) break;
            if (count > 0) sb.append(", ");
            sb.append(entry.getKey()).append("x").append(entry.getValue()[0])
                    .append("@").append(entry.getValue()[1]).append("m");
            count++;
        }

        return sb.toString();
    }

    private JsonArray getNearbyBlocks(ClientPlayerEntity player, ClientWorld world) {
        Map<String, BlockInfo> blocks = new HashMap<>();
        BlockPos playerPos = player.getBlockPos();

        for (int dx = -BLOCK_RADIUS; dx <= BLOCK_RADIUS; dx++) {
            for (int dy = -BLOCK_RADIUS; dy <= BLOCK_RADIUS; dy++) {
                for (int dz = -BLOCK_RADIUS; dz <= BLOCK_RADIUS; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    String id = Registries.BLOCK.getId(block).toString();

                    if (INTERESTING_BLOCKS.contains(id)) {
                        int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        BlockInfo info = blocks.computeIfAbsent(id, k -> new BlockInfo(id));
                        info.count++;
                        if (dist < info.nearestDist) {
                            info.nearestDist = dist;
                            info.nearestPos = pos;
                        }
                    }
                }
            }
        }

        // Sort by distance
        List<BlockInfo> sorted = new ArrayList<>(blocks.values());
        sorted.sort(Comparator.comparingInt(a -> a.nearestDist));

        JsonArray arr = new JsonArray();
        int count = 0;
        for (BlockInfo info : sorted) {
            if (count >= MAX_BLOCKS) break;
            JsonObject obj = new JsonObject();
            obj.addProperty("id", info.id.replace("minecraft:", ""));
            obj.addProperty("count", info.count);
            obj.addProperty("dist", info.nearestDist);
            arr.add(obj);
            count++;
        }

        return arr;
    }

    private String getNearbyBlocksCompact(ClientPlayerEntity player, ClientWorld world) {
        Map<String, int[]> blocks = new HashMap<>(); // id -> [count, minDist]
        BlockPos playerPos = player.getBlockPos();

        for (int dx = -BLOCK_RADIUS; dx <= BLOCK_RADIUS; dx++) {
            for (int dy = -BLOCK_RADIUS; dy <= BLOCK_RADIUS; dy++) {
                for (int dz = -BLOCK_RADIUS; dz <= BLOCK_RADIUS; dz++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    String id = Registries.BLOCK.getId(state.getBlock()).toString();

                    if (INTERESTING_BLOCKS.contains(id)) {
                        int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        String shortId = id.replace("minecraft:", "");
                        int[] data = blocks.computeIfAbsent(shortId, k -> new int[]{0, Integer.MAX_VALUE});
                        data[0]++;
                        data[1] = Math.min(data[1], dist);
                    }
                }
            }
        }

        // Sort by distance
        List<Map.Entry<String, int[]>> sorted = new ArrayList<>(blocks.entrySet());
        sorted.sort(Comparator.comparingInt(e -> e.getValue()[1]));

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, int[]> entry : sorted) {
            if (count >= 6) break;
            if (count > 0) sb.append(", ");
            sb.append(entry.getKey()).append("x").append(entry.getValue()[0])
                    .append("@").append(entry.getValue()[1]);
            count++;
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    // Helper classes
    private static class EntityInfo {
        String type;
        String category;
        int distance;
        int priority;

        EntityInfo(String type, String category, int distance, int priority) {
            this.type = type;
            this.category = category;
            this.distance = distance;
            this.priority = priority;
        }
    }

    private static class BlockInfo {
        String id;
        int count = 0;
        int nearestDist = Integer.MAX_VALUE;
        BlockPos nearestPos;

        BlockInfo(String id) {
            this.id = id;
        }
    }
}
