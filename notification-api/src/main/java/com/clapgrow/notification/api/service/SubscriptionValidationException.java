package com.clapgrow.notification.api.service;

/**
 * Exception thrown when subscription validation fails.
 * 
 * FUTURE ENHANCEMENT: This exception can be extended to include error categories
 * (TEMPORARY vs PERMANENT) to allow smarter retry logic:
 * 
 * - TEMPORARY: Provider downtime, rate limits, network issues (retry later)
 * - PERMANENT: Invalid API key, auth revoked (fail fast, don't retry)
 * 
 * Current implementation: All errors are treated as potentially temporary.
 * Registration and refresh operations will fallback gracefully.
 * 
 * Example future usage:
 * <pre>
 * try {
 *     subscriptionInfo = subscriptionService.getSubscriptionInfo(apiKey);
 * } catch (SubscriptionValidationException e) {
 *     if (e.getCategory() == ErrorCategory.PERMANENT) {
 *         // Fail fast - don't save invalid key
 *         throw e;
 *     } else {
 *         // Temporary issue - use fallback
 *         useDefaultSubscription();
 *     }
 * }
 * </pre>
 */
public class SubscriptionValidationException extends RuntimeException {
    
    /**
     * Error category (reason) for subscription validation failures.
     * 
     * FUTURE: When multiple providers are added, use this to implement smart retry logic:
     * - TEMPORARY: Fallback gracefully, retry later (provider downtime, rate limits)
     * - PERMANENT: Fail fast, don't save invalid key (invalid API key format)
     * - AUTH: Fail fast, mark as revoked (auth revoked, key expired)
     * 
     * Current implementation: All errors are caught as Exception and treated as potentially temporary.
     */
    public enum ErrorCategory {
        /**
         * Temporary errors that may resolve: provider downtime, rate limits, network issues.
         * Operations should fallback gracefully and retry later.
         */
        TEMPORARY,
        
        /**
         * Permanent errors that won't resolve: invalid API key format, malformed key.
         * Operations should fail fast and not retry. Don't save invalid keys.
         */
        PERMANENT,
        
        /**
         * Authentication errors: auth revoked, key expired, account suspended.
         * Operations should fail fast and mark key as invalid. Don't retry.
         */
        AUTH
    }
    
    private final ErrorCategory category;
    
    public SubscriptionValidationException(String message, ErrorCategory category) {
        super(message);
        this.category = category;
    }
    
    public SubscriptionValidationException(String message, ErrorCategory category, Throwable cause) {
        super(message, cause);
        this.category = category;
    }
    
    public ErrorCategory getCategory() {
        return category;
    }
}

