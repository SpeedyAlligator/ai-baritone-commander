package com.aicommander.execution;

import com.aicommander.config.ModConfig;
import com.aicommander.llm.LLMResponse;
import com.aicommander.util.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Executes planned actions via Baritone and vanilla client actions.
 */
public class ActionExecutor {
    
    private final BaritoneHelper baritone;
    private final ModConfig config;
    private final ScheduledExecutorService scheduler;
    
    private ExecutionState state = ExecutionState.IDLE;
    private final Queue<LLMResponse.Action> actionQueue = new ConcurrentLinkedQueue<>();
    private LLMResponse.Action currentAction = null;
    private int currentActionRetries = 0;
    private int totalSteps = 0;
    private String pendingQuestion = null;
    
    // Callbacks
    private Runnable onComplete;
    private Runnable onFail;
    
    public ActionExecutor() {
        this.baritone = new BaritoneHelper();
        this.config = ModConfig.getInstance();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AICommander-Executor");
            t.setDaemon(true);
            return t;
        });
        
        // Start the execution tick loop
        scheduler.scheduleAtFixedRate(this::tick, 1000, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Execute a list of actions from the LLM response.
     */
    public void execute(LLMResponse response, Runnable onComplete, Runnable onFail) {
        if (response == null || response.getActions() == null || response.getActions().isEmpty()) {
            ChatUtils.sendError("No actions to execute");
            if (onFail != null) onFail.run();
            return;
        }
        
        // Cancel current execution if not in queue mode
        if (!config.queueMode && state == ExecutionState.EXECUTING) {
            cancel();
        }
        
        this.onComplete = onComplete;
        this.onFail = onFail;
        
        // Add actions to queue
        actionQueue.addAll(response.getActions());
        totalSteps = 0;
        state = ExecutionState.EXECUTING;
        
        // Show the chat summary
        if (response.getChatSummary() != null) {
            ChatUtils.sendInfo(response.getChatSummary());
        }
        
        // Start executing
        executeNextAction();
    }
    
    /**
     * Periodic tick to check action completion and advance.
     */
    private void tick() {
        if (state != ExecutionState.EXECUTING) return;
        
        // Check if current action is complete
        if (currentAction != null && isCurrentActionComplete()) {
            if (config.showDebugMessages) {
                ChatUtils.sendDebug("Action complete: " + currentAction.getType());
            }
            executeNextAction();
        }
        
        // Check step limit
        if (totalSteps > config.maxStepsPerGoal) {
            ChatUtils.sendError("Max steps reached (" + config.maxStepsPerGoal + "). Stopping.");
            cancel();
        }
    }
    
    /**
     * Execute the next action in the queue.
     */
    private void executeNextAction() {
        if (actionQueue.isEmpty()) {
            state = ExecutionState.COMPLETED;
            ChatUtils.sendSuccess("All actions completed!");
            if (onComplete != null) {
                MinecraftClient.getInstance().execute(onComplete);
            }
            return;
        }
        
        currentAction = actionQueue.poll();
        currentActionRetries = 0;
        totalSteps++;
        
        if (config.showDebugMessages) {
            ChatUtils.sendDebug("Executing: " + currentAction.getType());
        }
        
        // Execute on main thread
        MinecraftClient.getInstance().execute(() -> executeAction(currentAction));
    }
    
    /**
     * Execute a single action.
     */
    private void executeAction(LLMResponse.Action action) {
        if (!baritone.isAvailable() && needsBaritone(action.getType())) {
            ChatUtils.sendError("Baritone is not available. Please install Baritone.");
            failExecution();
            return;
        }
        
        boolean success = false;
        
        switch (action.getType()) {
            case "goto" -> success = executeGoto(action);
            case "mine" -> success = executeMine(action);
            case "explore" -> success = executeExplore(action);
            case "follow" -> success = executeFollow(action);
            case "farm" -> success = executeFarm(action);
            case "stop" -> success = executeStop(action);
            case "ask" -> success = executeAsk(action);
            case "wait" -> success = executeWait(action);
            case "equip" -> success = executeEquip(action);
            case "drop" -> success = executeDrop(action);
            default -> {
                ChatUtils.sendWarning("Unknown action type: " + action.getType());
                success = true; // Skip unknown actions
            }
        }
        
        if (!success) {
            currentActionRetries++;
            if (currentActionRetries >= config.maxRetriesPerAction) {
                ChatUtils.sendError("Action failed after " + config.maxRetriesPerAction + " retries");
                executeNextAction(); // Move to next action
            } else {
                // Retry after a delay
                scheduler.schedule(() -> {
                    MinecraftClient.getInstance().execute(() -> executeAction(action));
                }, 2, TimeUnit.SECONDS);
            }
        }
    }
    
    private boolean executeGoto(LLMResponse.Action action) {
        if (action.hasCoordinates()) {
            int x = action.getX();
            int z = action.getZ();
            Integer y = action.getY();
            
            if (y != null) {
                ChatUtils.sendInfo("Going to " + x + ", " + y + ", " + z);
                return baritone.gotoCoordinates(x, y, z);
            } else {
                ChatUtils.sendInfo("Going to X=" + x + " Z=" + z);
                return baritone.gotoXZ(x, z);
            }
        } else if (action.hasBlock()) {
            String block = normalizeBlockId(action.getBlock());
            ChatUtils.sendInfo("Going to nearest " + block);
            return baritone.gotoBlock(block);
        }
        
        ChatUtils.sendError("Goto requires coordinates or block type");
        return false;
    }
    
    private boolean executeMine(LLMResponse.Action action) {
        if (!action.hasBlock()) {
            ChatUtils.sendError("Mine requires a block type");
            return false;
        }
        
        String block = normalizeBlockId(action.getBlock());
        int count = action.getCount();
        
        ChatUtils.sendInfo("Mining " + count + "x " + block);
        return baritone.mine(block, count);
    }
    
    private boolean executeExplore(LLMResponse.Action action) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        
        int x = (int) player.getX();
        int z = (int) player.getZ();
        
        ChatUtils.sendInfo("Exploring from current position");
        return baritone.explore(x, z);
    }
    
    private boolean executeFollow(LLMResponse.Action action) {
        String target = action.getTarget();
        if (target == null || target.isEmpty()) {
            ChatUtils.sendError("Follow requires a target");
            return false;
        }
        
        if (target.contains(":")) {
            // Entity type
            ChatUtils.sendInfo("Following " + target);
            return baritone.followEntityType(target);
        } else {
            // Player name
            ChatUtils.sendInfo("Following player: " + target);
            return baritone.followPlayer(target);
        }
    }
    
    private boolean executeFarm(LLMResponse.Action action) {
        int range = action.getRange() != null ? action.getRange() : 0;
        
        if (range > 0) {
            ChatUtils.sendInfo("Farming within " + range + " blocks");
        } else {
            ChatUtils.sendInfo("Farming nearby crops");
        }
        
        return baritone.farm(range);
    }
    
    private boolean executeStop(LLMResponse.Action action) {
        ChatUtils.sendInfo("Stopping all actions");
        baritone.stop();
        actionQueue.clear();
        state = ExecutionState.IDLE;
        return true;
    }
    
    private boolean executeAsk(LLMResponse.Action action) {
        String question = action.getQuestion();
        if (question == null || question.isEmpty()) {
            return true; // No question to ask
        }
        
        pendingQuestion = question;
        state = ExecutionState.WAITING_INPUT;
        ChatUtils.sendQuestion(question);
        return true;
    }
    
    private boolean executeWait(LLMResponse.Action action) {
        // Wait is handled by not marking as complete immediately
        int seconds = action.getCount(); // Reusing count field for seconds
        if (seconds <= 0) seconds = 5;
        
        final int waitSeconds = seconds;
        ChatUtils.sendInfo("Waiting " + waitSeconds + " seconds...");
        
        scheduler.schedule(() -> {
            if (state == ExecutionState.EXECUTING) {
                executeNextAction();
            }
        }, waitSeconds, TimeUnit.SECONDS);
        
        return true;
    }
    
    private boolean executeEquip(LLMResponse.Action action) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        
        Integer slot = action.getSlot();
        String itemId = action.getItem();
        
        if (slot != null && slot >= 0 && slot <= 8) {
            player.getInventory().selectedSlot = slot;
            ChatUtils.sendInfo("Equipped slot " + slot);
            return true;
        } else if (itemId != null) {
            // Find the item in hotbar
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (!stack.isEmpty()) {
                    Identifier id = Registries.ITEM.getId(stack.getItem());
                    if (id.toString().equals(normalizeBlockId(itemId))) {
                        player.getInventory().selectedSlot = i;
                        ChatUtils.sendInfo("Equipped " + itemId);
                        return true;
                    }
                }
            }
            ChatUtils.sendWarning("Item not found in hotbar: " + itemId);
            return true; // Don't fail, just warn
        }
        
        return false;
    }
    
    private boolean executeDrop(LLMResponse.Action action) {
        // Drop is a bit complex - would need to implement inventory interaction
        // For now, just log it
        ChatUtils.sendWarning("Drop action not yet implemented");
        return true;
    }
    
    /**
     * Check if the current action is complete.
     */
    private boolean isCurrentActionComplete() {
        if (currentAction == null) return true;
        
        switch (currentAction.getType()) {
            case "goto", "mine", "explore", "follow", "farm" -> {
                // These use Baritone - check if Baritone is still pathing
                return !baritone.isPathing();
            }
            case "wait" -> {
                // Wait completion is handled by scheduler
                return false;
            }
            case "ask" -> {
                // Ask completion is handled by input handler
                return state != ExecutionState.WAITING_INPUT;
            }
            default -> {
                return true; // Instant actions
            }
        }
    }
    
    /**
     * Check if an action type requires Baritone.
     */
    private boolean needsBaritone(String actionType) {
        return switch (actionType) {
            case "goto", "mine", "explore", "follow", "farm" -> true;
            default -> false;
        };
    }
    
    /**
     * Normalize a block ID to include minecraft: prefix if missing.
     */
    private String normalizeBlockId(String blockId) {
        if (blockId == null) return "minecraft:air";
        if (!blockId.contains(":")) {
            return "minecraft:" + blockId;
        }
        return blockId;
    }
    
    /**
     * Handle player input for clarification questions.
     */
    public boolean handleInput(String input) {
        if (state != ExecutionState.WAITING_INPUT) {
            return false;
        }
        
        // Store the input and continue
        state = ExecutionState.EXECUTING;
        pendingQuestion = null;
        
        // The input handling would need to re-query the LLM with context
        // For now, just continue to next action
        executeNextAction();
        
        return true;
    }
    
    /**
     * Cancel execution.
     */
    public void cancel() {
        baritone.stop();
        actionQueue.clear();
        currentAction = null;
        state = ExecutionState.IDLE;
        pendingQuestion = null;
    }

    /**
     * Bulletproof cancel - stops everything and resets all state.
     * This is the nuclear option for /ai stop.
     */
    public void cancelAll() {
        // Stop Baritone multiple times to ensure it's really stopped
        baritone.stop();

        // Clear the action queue
        actionQueue.clear();

        // Reset all state
        currentAction = null;
        currentActionRetries = 0;
        totalSteps = 0;
        pendingQuestion = null;
        state = ExecutionState.IDLE;

        // Clear callbacks
        onComplete = null;
        onFail = null;

        // Send another stop to Baritone just to be sure
        baritone.stop();
    }

    /**
     * Get the number of actions in the queue.
     */
    public int getQueueSize() {
        return actionQueue.size();
    }
    
    /**
     * Fail the current execution.
     */
    private void failExecution() {
        cancel();
        state = ExecutionState.FAILED;
        if (onFail != null) {
            MinecraftClient.getInstance().execute(onFail);
        }
    }
    
    /**
     * Get the current execution state.
     */
    public ExecutionState getState() {
        return state;
    }
    
    /**
     * Check if executor is busy.
     */
    public boolean isBusy() {
        return state == ExecutionState.EXECUTING || state == ExecutionState.PLANNING;
    }
    
    /**
     * Get pending question if any.
     */
    public String getPendingQuestion() {
        return pendingQuestion;
    }
    
    /**
     * Get the Baritone helper.
     */
    public BaritoneHelper getBaritone() {
        return baritone;
    }
    
    /**
     * Shutdown the executor.
     */
    public void shutdown() {
        scheduler.shutdown();
    }
}
