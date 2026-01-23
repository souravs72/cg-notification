package com.clapgrow.notification.common.retry;

/**
 * Resolves retry policy based on failure classification.
 * 
 * Maps failure classifications to retry strategies:
 * - PERMANENT: No retry (send to DLQ immediately)
 * - RATE_LIMIT: Retry with exponential backoff
 * - TRANSIENT: Retry with standard strategy
 * 
 * This allows different failure types to have different retry behaviors
 * without coupling the classification logic to retry logic.
 * 
 * Shared across all notification workers (Email, WhatsApp, SMS, etc.)
 * to ensure consistent retry behavior.
 */
public interface RetryPolicyResolver {
    
    /**
     * Resolve retry policy for a given failure classification.
     * 
     * @param classification Failure classification
     * @return Retry policy configuration
     */
    RetryPolicy resolve(FailureClassification classification);
    
    /**
     * Retry policy configuration.
     * 
     * Defines retry behavior for different failure types.
     * Shared across all notification workers for consistency.
     */
    record RetryPolicy(
        boolean shouldRetry,
        long initialDelayMs,
        long maxDelayMs,
        double backoffMultiplier,
        int maxRetries
    ) {
        /**
         * No retry policy (for permanent failures).
         */
        public static RetryPolicy noRetry() {
            return new RetryPolicy(false, 0, 0, 1.0, 0);
        }
        
        /**
         * Standard retry policy (for transient failures).
         */
        public static RetryPolicy standard() {
            return new RetryPolicy(true, 1000, 60000, 2.0, 3);
        }
        
        /**
         * Exponential backoff retry policy (for rate limits).
         */
        public static RetryPolicy exponentialBackoff() {
            return new RetryPolicy(true, 5000, 300000, 2.0, 5);
        }
    }
}



