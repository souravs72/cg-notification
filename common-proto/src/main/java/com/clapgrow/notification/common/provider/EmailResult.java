package com.clapgrow.notification.common.provider;

/**
 * Email provider result.
 * 
 * Immutable result object for email sending operations.
 * Uses record for immutability and concise syntax.
 * 
 * Example usage:
 * <pre>
 * EmailResult result = emailProvider.sendEmail(payload);
 * if (!result.isSuccess()) {
 *     log.error("Email failed: {}", result.getErrorMessage());
 *     if (result.getErrorCategory() == ProviderErrorCategory.TEMPORARY) {
 *         // Retry later
 *     }
 * }
 * </pre>
 */
public record EmailResult(
    boolean success,
    String errorMessage,
    ProviderErrorCategory errorCategory
) implements MessageResult {
    
    /**
     * Create a successful result.
     * 
     * @return Success result
     */
    public static EmailResult createSuccess() {
        return new EmailResult(true, null, null);
    }
    
    /**
     * Create a failure result.
     * 
     * @param errorMessage Error message
     * @param errorCategory Error category
     * @return Failure result
     */
    public static EmailResult createFailure(String errorMessage, ProviderErrorCategory errorCategory) {
        return new EmailResult(false, errorMessage, errorCategory);
    }
    
    /**
     * Create a failure result with TEMPORARY category (default).
     * 
     * @param errorMessage Error message
     * @return Failure result
     */
    public static EmailResult createFailure(String errorMessage) {
        return new EmailResult(false, errorMessage, ProviderErrorCategory.TEMPORARY);
    }
    
    // Implement MessageResult interface methods
    @Override
    public boolean isSuccess() {
        return success;
    }
    
    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public ProviderErrorCategory getErrorCategory() {
        return errorCategory;
    }
}

