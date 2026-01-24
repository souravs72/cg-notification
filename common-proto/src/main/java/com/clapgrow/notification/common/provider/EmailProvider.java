package com.clapgrow.notification.common.provider;

/**
 * Email provider interface.
 * 
 * Abstraction for email sending providers (SendGrid, Mailgun, etc.).
 * Enables provider-agnostic email sending and easy provider switching.
 * 
 * Implementation guidelines:
 * - Handle errors gracefully and categorize them
 * - Never log API keys or sensitive data
 * - Return appropriate error categories for retry logic
 * 
 * Example usage:
 * <pre>
 * EmailProvider provider = providerRegistry.getEmailProvider("sendgrid");
 * EmailResult result = provider.sendEmail(payload);
 * if (!result.isSuccess()) {
 *     handleError(result);
 * }
 * </pre>
 * 
 * @param <T> NotificationPayload type (module-specific)
 */
public interface EmailProvider<T> {
    
    /**
     * Send an email using the provider.
     * 
     * @param payload Email payload containing recipient, subject, body, etc.
     * @return EmailResult indicating success or failure with error details
     */
    EmailResult sendEmail(T payload);
    
    /**
     * Get the provider name.
     * 
     * @return ProviderName enum value
     */
    ProviderName getProviderName();
    
    /**
     * Check if the provider is configured and ready to send emails.
     * 
     * @return true if configured, false otherwise
     */
    default boolean isConfigured() {
        return true; // Default implementation - override if needed
    }
}







