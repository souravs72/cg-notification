package com.clapgrow.notification.common.retry;

/**
 * Classification of message delivery failures.
 * 
 * Used to determine retry strategy and behavior:
 * - PERMANENT: Should not be retried (e.g., invalid API key, authentication errors)
 * - TRANSIENT: Should be retried (e.g., network errors, temporary service unavailability)
 * - RATE_LIMIT: Should be retried with backoff (e.g., 429 Too Many Requests)
 * 
 * This enum is shared across all notification workers (Email, WhatsApp, SMS, etc.)
 * to ensure consistent retry behavior across all channels.
 */
public enum FailureClassification {
    PERMANENT,   // Permanent failure - do not retry
    TRANSIENT,   // Transient failure - retry with standard strategy
    RATE_LIMIT   // Rate limit - retry with exponential backoff
}





