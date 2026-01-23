package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Validates status transitions to prevent invalid state changes.
 * 
 * ⚠️ CRITICAL INFRASTRUCTURE: This validator must remain deterministic.
 * 
 * Requirements:
 * - NO environment flags
 * - NO database calls
 * - NO time-based logic
 * - Pure enum-map based validation
 * 
 * This ensures consistent behavior across all environments and prevents
 * race conditions or non-deterministic validation failures.
 * 
 * Valid transitions:
 * - PENDING → SENT, DELIVERED, FAILED, RETRYING
 * - SCHEDULED → PENDING, FAILED
 * - RETRYING → PENDING, FAILED
 * - SENT → DELIVERED, FAILED
 * - DELIVERED → (terminal, no transitions)
 * - FAILED → RETRYING (via retry service)
 * - BOUNCED → (terminal, no transitions)
 * - REJECTED → (terminal, no transitions)
 * 
 * Invalid transitions (prevented):
 * - DELIVERED → FAILED, RETRYING, PENDING
 * - DELIVERED → DELIVERED (no-op but logged)
 * - Any terminal state → non-terminal state
 */
@Component
@Slf4j
public class StatusTransitionValidator {
    
    // Terminal states - cannot transition from these
    private static final Set<DeliveryStatus> TERMINAL_STATES = EnumSet.of(
        DeliveryStatus.DELIVERED,
        DeliveryStatus.BOUNCED,
        DeliveryStatus.REJECTED
    );
    
    // Valid transitions map: from -> to
    private static final Map<DeliveryStatus, Set<DeliveryStatus>> VALID_TRANSITIONS = Map.of(
        DeliveryStatus.PENDING, EnumSet.of(
            DeliveryStatus.SENT,
            DeliveryStatus.DELIVERED,
            DeliveryStatus.FAILED,
            DeliveryStatus.RETRYING
        ),
        DeliveryStatus.SCHEDULED, EnumSet.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.FAILED
        ),
        DeliveryStatus.RETRYING, EnumSet.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.FAILED
        ),
        DeliveryStatus.SENT, EnumSet.of(
            DeliveryStatus.DELIVERED,
            DeliveryStatus.FAILED
        ),
        DeliveryStatus.FAILED, EnumSet.of(
            DeliveryStatus.RETRYING,
            DeliveryStatus.PENDING  // When retry succeeds and message is requeued
        )
    );
    
    /**
     * Validate if a status transition is allowed.
     * 
     * @param fromStatus Current status
     * @param toStatus Desired new status
     * @return true if transition is valid, false otherwise
     */
    public boolean isValidTransition(DeliveryStatus fromStatus, DeliveryStatus toStatus) {
        // Same status is always valid (no-op)
        if (fromStatus == toStatus) {
            return true;
        }
        
        // Cannot transition from terminal states
        if (TERMINAL_STATES.contains(fromStatus)) {
            log.warn("Invalid status transition attempted: {} → {} ({} is a terminal state)", 
                fromStatus, toStatus, fromStatus);
            return false;
        }
        
        // Check if transition is in valid transitions map
        Set<DeliveryStatus> allowedTransitions = VALID_TRANSITIONS.get(fromStatus);
        if (allowedTransitions == null || !allowedTransitions.contains(toStatus)) {
            log.warn("Invalid status transition attempted: {} → {} (not in allowed transitions)", 
                fromStatus, toStatus);
            return false;
        }
        
        return true;
    }
    
    /**
     * Assert that a status transition is valid, throwing exception if not.
     * 
     * @param fromStatus Current status
     * @param toStatus Desired new status
     * @throws IllegalArgumentException if transition is invalid
     */
    public void assertValidTransition(DeliveryStatus fromStatus, DeliveryStatus toStatus) {
        if (!isValidTransition(fromStatus, toStatus)) {
            throw new IllegalArgumentException(
                String.format("Invalid status transition: %s → %s", fromStatus, toStatus)
            );
        }
    }
}

