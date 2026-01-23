package com.clapgrow.notification.common.provider;

/**
 * Provider error category for retry logic and error handling.
 * 
 * Enables provider-agnostic retry strategies:
 * - TEMPORARY: Retry with exponential backoff
 * - PERMANENT: Don't retry, mark as failed
 * - AUTH: Alert admin, don't retry
 * - CONFIG: Misconfiguration, alert admin
 * 
 * Example usage:
 * <pre>
 * if (result.getErrorCategory() == ProviderErrorCategory.TEMPORARY) {
 *     // Retry later
 * } else if (result.getErrorCategory() == ProviderErrorCategory.AUTH) {
 *     // Alert admin about auth issue
 * }
 * </pre>
 */
public enum ProviderErrorCategory {
    /**
     * Temporary errors that may resolve: rate limits, timeouts, server errors.
     * Operations should retry with exponential backoff.
     */
    TEMPORARY,
    
    /**
     * Permanent errors that won't resolve: invalid request format, malformed data.
     * Operations should fail fast and not retry.
     */
    PERMANENT,
    
    /**
     * Authentication/authorization errors: invalid API key, revoked access.
     * Operations should alert admin and not retry.
     */
    AUTH,
    
    /**
     * Configuration errors: missing required config, invalid settings.
     * Operations should alert admin and not retry.
     */
    CONFIG
}





