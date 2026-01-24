package com.clapgrow.notification.api.enums;

/**
 * Source of a message status history entry.
 * 
 * Used to track where the history entry came from for deduplication and audit trail.
 */
public enum HistorySource {
    API,      // Created by MessageStatusHistoryService (emits metrics, validates transitions)
    TRIGGER,  // Created by database trigger (ensures no status change is missed)
    WORKER    // Created by worker service (if needed in future)
}








