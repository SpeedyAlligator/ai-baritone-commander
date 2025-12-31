package com.aicommander.planning;

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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.*;

/**
 * Collects the current world state for the LLM to make decisions.
 */
public class WorldStateCollector {
    
    private final MinecraftClient client;
    private final int blockScanRadius;
    private final int entityScanRadius;
    
    public WorldStateCollector(int blockScanRadius, int entityScanRadius) {
        this.client = MinecraftClient.getInstance();
        this.blockScanRadius = blockScanRadius;
        this.entityScanRadius = entityScanRadius;
    }
    
    /**
     * Collect the current world state as a JSON object.
     */
    public JsonObject collectState() {
        JsonObject state = new JsonObject();
        
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        
        if (player == null || world == null) {
            state.addProperty("error", "Player or world not available");
            return state;
        }
        
        // Player position
        JsonObject position = new JsonObject();
        position.addProperty("x", (int) player.getX());
        position.addProperty("y", (int) player.getY());
        position.addProperty("z", (int) player.getZ());
        state.add("position", position);
        
        // Dimension
        Identifier dimensionId = world.getRegistryKey().getValue();
        state.addProperty("dimension", dimensionId.toString());
        
        // Health and food
        state.addProperty("health", player.getHealth());
        state.addProperty("max_health", player.getMaxHealth());
        state.addProperty("food", player.getHungerManager().getFoodLevel());
        state.addProperty("saturation", player.getHungerManager().getSaturationLevel());
        
        // Experience
        state.addProperty("experience_level", player.experienceLevel);
        
        // Armor
        state.addProperty("armor", player.getArmor());
        
        // Time and weather
        long timeOfDay = world.getTimeOfDay() % 24000;
        state.addProperty("time_of_day", timeOfDay);
        state.addProperty("is_day", timeOfDay < 12000);
        state.addProperty("is_raining", world.isRaining());
        state.addProperty("is_thundering", world.isThundering());
        
        // Inventory summary (counts by item)
        state.add("inventory", collectInventory(player));
        
        // Held item
        ItemStack heldItem = player.getMainHandStack();
        if (!heldItem.isEmpty()) {
            state.addProperty("held_item", getItemId(heldItem.getItem()));
            state.addProperty("held_item_count", heldItem.getCount());
        }
        
        // Nearby blocks (grouped by type)
        state.add("nearby_blocks", collectNearbyBlocks(world, player.getBlockPos()));
        
        // Nearby entities
        state.add("nearby_entities", collectNearbyEntities(world, player));
        
        // Check if Baritone is active
        state.addProperty("baritone_active", isBaritoneActive());
        
        // Biome
        state.addProperty("biome", world.getBiome(player.getBlockPos()).getKey()
                .map(key -> key.getValue().toString()).orElse("unknown"));
        
        return state;
    }
    
    /**
     * Collect inventory as item counts.
     */
    private JsonObject collectInventory(ClientPlayerEntity player) {
        JsonObject inventory = new JsonObject();
        Map<String, Integer> itemCounts = new HashMap<>();
        
        // Main inventory
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                String itemId = getItemId(stack.getItem());
                itemCounts.merge(itemId, stack.getCount(), Integer::sum);
            }
        }
        
        // Sort by count (descending) and add to JSON
        itemCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> inventory.addProperty(entry.getKey(), entry.getValue()));
        
        return inventory;
    }
    
    /**
     * Collect nearby blocks grouped by type.
     */
    private JsonObject collectNearbyBlocks(ClientWorld world, BlockPos center) {
        Map<String, List<int[]>> blockLocations = new HashMap<>();
        
        // Scan nearby blocks
        for (int x = -blockScanRadius; x <= blockScanRadius; x++) {
            for (int y = -blockScanRadius / 2; y <= blockScanRadius / 2; y++) {
                for (int z = -blockScanRadius; z <= blockScanRadius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    
                    // Skip air and common blocks to reduce noise
                    String blockId = getBlockId(block);
                    if (isInterestingBlock(blockId)) {
                        blockLocations.computeIfAbsent(blockId, k -> new ArrayList<>())
                                .add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
                    }
                }
            }
        }
        
        // Convert to JSON (only include first few locations per type)
        JsonObject result = new JsonObject();
        blockLocations.forEach((blockId, positions) -> {
            JsonObject blockInfo = new JsonObject();
            blockInfo.addProperty("count", positions.size());
            
            // Include up to 3 nearest positions
            JsonArray posArray = new JsonArray();
            positions.stream()
                    .sorted(Comparator.comparingDouble(p -> 
                            Math.sqrt(Math.pow(p[0] - center.getX(), 2) + 
                                      Math.pow(p[1] - center.getY(), 2) + 
                                      Math.pow(p[2] - center.getZ(), 2))))
                    .limit(3)
                    .forEach(p -> {
                        JsonArray coord = new JsonArray();
                        coord.add(p[0]);
                        coord.add(p[1]);
                        coord.add(p[2]);
                        posArray.add(coord);
                    });
            blockInfo.add("nearest", posArray);
            
            result.add(blockId, blockInfo);
        });
        
        return result;
    }
    
    /**
     * Check if a block is interesting enough to report.
     */
    private boolean isInterestingBlock(String blockId) {
        // Skip very common blocks
        Set<String> skipBlocks = Set.of(
                "minecraft:air", "minecraft:stone", "minecraft:dirt", 
                "minecraft:grass_block", "minecraft:water", "minecraft:sand",
                "minecraft:gravel", "minecraft:bedrock", "minecraft:deepslate",
                "minecraft:cobblestone", "minecraft:netherrack", "minecraft:end_stone"
        );
        
        // Include ores, wood, crafting stations, chests, etc.
        return !skipBlocks.contains(blockId) && (
                blockId.contains("ore") ||
                blockId.contains("log") ||
                blockId.contains("wood") ||
                blockId.contains("chest") ||
                blockId.contains("furnace") ||
                blockId.contains("crafting") ||
                blockId.contains("anvil") ||
                blockId.contains("enchanting") ||
                blockId.contains("brewing") ||
                blockId.contains("bed") ||
                blockId.contains("door") ||
                blockId.contains("crop") ||
                blockId.contains("wheat") ||
                blockId.contains("carrot") ||
                blockId.contains("potato") ||
                blockId.contains("diamond") ||
                blockId.contains("emerald") ||
                blockId.contains("gold") ||
                blockId.contains("iron") ||
                blockId.contains("coal") ||
                blockId.contains("lapis") ||
                blockId.contains("redstone") ||
                blockId.contains("obsidian") ||
                blockId.contains("portal") ||
                blockId.contains("spawner")
        );
    }
    
    /**
     * Collect nearby entities.
     */
    private JsonArray collectNearbyEntities(ClientWorld world, ClientPlayerEntity player) {
        JsonArray entities = new JsonArray();
        
        Box scanBox = new Box(
                player.getX() - entityScanRadius, player.getY() - entityScanRadius, player.getZ() - entityScanRadius,
                player.getX() + entityScanRadius, player.getY() + entityScanRadius, player.getZ() + entityScanRadius
        );
        
        Map<String, List<Entity>> entityGroups = new HashMap<>();
        
        for (Entity entity : world.getEntitiesByClass(Entity.class, scanBox, e -> e != player)) {
            String type = getEntityType(entity);
            entityGroups.computeIfAbsent(type, k -> new ArrayList<>()).add(entity);
        }
        
        entityGroups.forEach((type, entityList) -> {
            JsonObject entityGroup = new JsonObject();
            entityGroup.addProperty("type", type);
            entityGroup.addProperty("count", entityList.size());
            
            // Get nearest entity of this type
            Entity nearest = entityList.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceTo(player)))
                    .orElse(null);
            
            if (nearest != null) {
                JsonObject nearestPos = new JsonObject();
                nearestPos.addProperty("x", (int) nearest.getX());
                nearestPos.addProperty("y", (int) nearest.getY());
                nearestPos.addProperty("z", (int) nearest.getZ());
                nearestPos.addProperty("distance", (int) nearest.distanceTo(player));
                
                // Include name for players
                if (nearest instanceof PlayerEntity otherPlayer) {
                    nearestPos.addProperty("name", otherPlayer.getName().getString());
                }
                
                // Include health for living entities
                if (nearest instanceof LivingEntity living) {
                    nearestPos.addProperty("health", living.getHealth());
                }
                
                entityGroup.add("nearest", nearestPos);
            }
            
            // Categorize
            if (!entityList.isEmpty()) {
                Entity sample = entityList.get(0);
                if (sample instanceof HostileEntity) {
                    entityGroup.addProperty("category", "hostile");
                } else if (sample instanceof AnimalEntity) {
                    entityGroup.addProperty("category", "animal");
                } else if (sample instanceof PlayerEntity) {
                    entityGroup.addProperty("category", "player");
                } else {
                    entityGroup.addProperty("category", "other");
                }
            }
            
            entities.add(entityGroup);
        });
        
        return entities;
    }
    
    private String getEntityType(Entity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return id.toString();
    }
    
    private String getItemId(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id.toString();
    }
    
    private String getBlockId(Block block) {
        Identifier id = Registries.BLOCK.getId(block);
        return id.toString();
    }
    
    /**
     * Check if Baritone is currently running a process.
     */
    private boolean isBaritoneActive() {
        try {
            Class<?> baritoneAPIClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = baritoneAPIClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            Object isPathing = pathingBehavior.getClass().getMethod("isPathing").invoke(pathingBehavior);
            return (Boolean) isPathing;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get a compact string representation of the state for the LLM prompt.
     */
    public String getStateString() {
        return collectState().toString();
    }
}
