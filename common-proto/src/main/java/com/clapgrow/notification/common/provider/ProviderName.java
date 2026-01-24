package com.clapgrow.notification.common.provider;

/**
 * Provider name enumeration.
 * 
 * Used to identify messaging providers in a type-safe manner.
 * Prevents magic strings and enables compile-time safety.
 * 
 * Example usage:
 * <pre>
 * ProviderName provider = ProviderName.SENDGRID;
 * if (provider == ProviderName.WASENDER) {
 *     // Handle WASender-specific logic
 * }
 * </pre>
 */
public enum ProviderName {
    /**
     * SendGrid email provider
     */
    SENDGRID,
    
    /**
     * WASender WhatsApp provider
     */
    WASENDER,
    
    /**
     * Twilio WhatsApp/SMS provider (future)
     */
    TWILIO,
    
    /**
     * Mailgun email provider (future)
     */
    MAILGUN,
    
    /**
     * Meta Cloud API WhatsApp provider (future)
     */
    META_CLOUD_API;
    
    /**
     * Get provider name as lowercase string for configuration/storage.
     * 
     * @return Lowercase provider name (e.g., "sendgrid", "wasender")
     */
    public String toConfigValue() {
        return name().toLowerCase();
    }
    
    /**
     * Parse provider name from string (case-insensitive).
     * 
     * @param name Provider name string
     * @return ProviderName enum value
     * @throws IllegalArgumentException if name doesn't match any provider
     */
    public static ProviderName fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }
        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown provider name: " + name + ". Available: " + 
                java.util.Arrays.toString(values()), e);
        }
    }
}







