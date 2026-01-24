package com.clapgrow.notification.common.provider;

/**
 * WhatsApp provider interface.
 * 
 * Abstraction for WhatsApp sending providers (WASender, Twilio, Meta Cloud API, etc.).
 * Enables provider-agnostic WhatsApp sending and easy provider switching.
 * 
 * Implementation guidelines:
 * - Handle errors gracefully and categorize them
 * - Never log API keys or sensitive data
 * - Return appropriate error categories for retry logic
 * - Include HTTP-specific details (status code, response body) when available
 * 
 * Example usage:
 * <pre>
 * WhatsAppProvider provider = providerRegistry.getWhatsAppProvider("wasender");
 * WhatsAppResult result = provider.sendMessage(payload);
 * if (!result.isSuccess()) {
 *     handleError(result);
 * }
 * </pre>
 * 
 * @param <T> NotificationPayload type (module-specific)
 */
public interface WhatsAppProvider<T> {
    
    /**
     * Send a WhatsApp message using the provider.
     * 
     * @param payload WhatsApp payload containing recipient, message content, media, etc.
     * @return WhatsAppResult indicating success or failure with error details
     */
    WhatsAppResult sendMessage(T payload);
    
    /**
     * Get the provider name.
     * 
     * @return ProviderName enum value
     */
    ProviderName getProviderName();
    
    /**
     * Check if the provider is configured and ready to send messages.
     * 
     * @return true if configured, false otherwise
     */
    default boolean isConfigured() {
        return true; // Default implementation - override if needed
    }
}







