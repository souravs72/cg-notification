package com.clapgrow.notification.common.provider;

/**
 * WhatsApp provider result.
 * 
 * Immutable result object for WhatsApp sending operations.
 * Includes HTTP-specific fields (status code, response body) that email doesn't need.
 * 
 * Example usage:
 * <pre>
 * WhatsAppResult result = whatsAppProvider.sendMessage(payload);
 * if (!result.isSuccess()) {
 *     log.error("WhatsApp failed: {} (HTTP {})", 
 *         result.getErrorMessage(), result.httpStatusCode());
 *     if (result.getErrorCategory() == ProviderErrorCategory.TEMPORARY) {
 *         // Retry later
 *     }
 * }
 * </pre>
 */
public record WhatsAppResult(
    boolean success,
    String errorMessage,
    String errorDetails,
    Integer httpStatusCode,
    String responseBody,
    ProviderErrorCategory errorCategory
) implements MessageResult {
    
    /**
     * Create a successful result.
     * 
     * @return Success result
     */
    public static WhatsAppResult createSuccess() {
        return new WhatsAppResult(true, null, null, null, null, null);
    }
    
    /**
     * Create a failure result.
     * 
     * @param errorMessage Error message
     * @param errorDetails Detailed error information
     * @param httpStatusCode HTTP status code (if applicable)
     * @param responseBody HTTP response body (if applicable)
     * @param errorCategory Error category
     * @return Failure result
     */
    public static WhatsAppResult createFailure(
        String errorMessage,
        String errorDetails,
        Integer httpStatusCode,
        String responseBody,
        ProviderErrorCategory errorCategory
    ) {
        return new WhatsAppResult(
            false, errorMessage, errorDetails, 
            httpStatusCode, responseBody, errorCategory
        );
    }
    
    /**
     * Create a failure result with TEMPORARY category (default).
     * 
     * @param errorMessage Error message
     * @param errorDetails Detailed error information
     * @return Failure result
     */
    public static WhatsAppResult createFailure(String errorMessage, String errorDetails) {
        return new WhatsAppResult(
            false, errorMessage, errorDetails, 
            null, null, ProviderErrorCategory.TEMPORARY
        );
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

