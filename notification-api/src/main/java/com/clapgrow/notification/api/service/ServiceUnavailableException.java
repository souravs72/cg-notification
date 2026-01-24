package com.clapgrow.notification.api.service;

/**
 * Exception thrown when a service is temporarily unavailable (e.g., provider downtime, rate limits).
 * 
 * FUTURE: Use this instead of IllegalArgumentException for temporary provider failures.
 * Allows controllers to map to HTTP 503 Service Unavailable.
 * 
 * Example usage:
 * <pre>
 * try {
 *     subscriptionInfo = subscriptionService.getSubscriptionInfo(apiKey);
 * } catch (SubscriptionValidationException e) {
 *     if (e.getCategory() == ErrorCategory.TEMPORARY) {
 *         throw new ServiceUnavailableException("Provider temporarily unavailable", e);
 *     }
 * }
 * </pre>
 */
public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String message) {
        super(message);
    }
    
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}







