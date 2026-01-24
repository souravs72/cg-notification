package com.clapgrow.notification.common.provider;

/**
 * Base interface for all messaging provider results.
 * 
 * Provides common contract for success/failure and error information.
 * Channel-specific providers extend this with additional fields as needed.
 */
public interface MessageResult {
    /**
     * Check if the operation was successful.
     * 
     * @return true if successful, false otherwise
     */
    boolean isSuccess();
    
    /**
     * Get error message if operation failed.
     * 
     * @return Error message, or null if successful
     */
    String getErrorMessage();
    
    /**
     * Get error category for retry logic.
     * 
     * @return Error category, or null if successful
     */
    default ProviderErrorCategory getErrorCategory() {
        return null;
    }
}







