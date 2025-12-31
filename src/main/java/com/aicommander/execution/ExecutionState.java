package com.aicommander.execution;

/**
 * Represents the current state of action execution.
 */
public enum ExecutionState {
    IDLE,           // No actions running
    PLANNING,       // Waiting for LLM response
    EXECUTING,      // Currently executing actions
    WAITING_INPUT,  // Waiting for player clarification
    PAUSED,         // Execution paused
    COMPLETED,      // All actions completed
    FAILED          // Execution failed
}
