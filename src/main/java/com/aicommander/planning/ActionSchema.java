package com.aicommander.planning;

/**
 * Defines the allowed action schema for the LLM.
 * This schema is included in the prompt to constrain LLM output.
 */
public class ActionSchema {
    
    public static final String SCHEMA = """
        {
          "actions": [
            // Array of actions to execute in order
          ],
          "reason": "Brief explanation of why these actions were chosen",
          "chat_summary": "Short message to show the player (1-2 sentences)"
        }
        
        ALLOWED ACTION TYPES:
        
        1. goto - Navigate to coordinates or a block type
           {"type": "goto", "x": 123, "y": 64, "z": -200}
           {"type": "goto", "block": "minecraft:diamond_ore"}
           {"type": "goto", "block": "minecraft:crafting_table"}
        
        2. mine - Mine a specific block type
           {"type": "mine", "block": "minecraft:oak_log", "count": 16}
           {"type": "mine", "block": "minecraft:diamond_ore", "count": 5}
           {"type": "mine", "block": "minecraft:iron_ore"}  // count defaults to 64
        
        3. explore - Explore the world in a direction
           {"type": "explore", "distance": 100}
           {"type": "explore"}  // distance defaults to 100
        
        4. follow - Follow a player or entity
           {"type": "follow", "target": "player_name"}
           {"type": "follow", "target": "minecraft:cow"}
        
        5. farm - Automatically harvest and replant crops
           {"type": "farm", "range": 30}
           {"type": "farm"}  // range defaults to infinite
        
        6. stop - Stop all current actions
           {"type": "stop"}
        
        7. ask - Ask the player for clarification (when instruction is ambiguous)
           {"type": "ask", "question": "Did you mean oak logs or birch logs?"}
        
        8. wait - Wait for a condition or time
           {"type": "wait", "seconds": 10}
        
        9. equip - Switch held item (slot 0-8 for hotbar)
           {"type": "equip", "slot": 0}
           {"type": "equip", "item": "minecraft:diamond_pickaxe"}
        
        10. drop - Drop items
            {"type": "drop", "item": "minecraft:cobblestone", "count": 64}
        
        BLOCK NAME EXAMPLES:
        - Ores: minecraft:coal_ore, minecraft:iron_ore, minecraft:gold_ore, minecraft:diamond_ore, minecraft:emerald_ore, minecraft:lapis_ore, minecraft:redstone_ore, minecraft:copper_ore
        - Deepslate ores: minecraft:deepslate_iron_ore, minecraft:deepslate_diamond_ore, etc.
        - Wood: minecraft:oak_log, minecraft:birch_log, minecraft:spruce_log, minecraft:jungle_log, minecraft:acacia_log, minecraft:dark_oak_log, minecraft:mangrove_log, minecraft:cherry_log
        - Stone: minecraft:stone, minecraft:cobblestone, minecraft:granite, minecraft:diorite, minecraft:andesite
        - Crafting: minecraft:crafting_table, minecraft:furnace, minecraft:anvil, minecraft:enchanting_table, minecraft:brewing_stand
        - Storage: minecraft:chest, minecraft:barrel, minecraft:ender_chest, minecraft:shulker_box
        - Crops: minecraft:wheat, minecraft:carrots, minecraft:potatoes, minecraft:beetroots, minecraft:melon, minecraft:pumpkin
        
        IMPORTANT RULES:
        1. Always use full block IDs with minecraft: prefix
        2. If the player already has enough of an item, don't gather more - tell them
        3. If a task requires tools the player doesn't have, mine resources for tools first
        4. Break complex tasks into sequential actions
        5. If the instruction is unclear, use "ask" to get clarification
        6. Keep chat_summary brief and informative
        7. Consider player safety - avoid actions that might kill them
        """;
    
    public static String getPromptSchema() {
        return SCHEMA;
    }
}
