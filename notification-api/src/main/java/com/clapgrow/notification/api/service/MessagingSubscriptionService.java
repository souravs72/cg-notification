package com.clapgrow.notification.api.service;

/**
 * Abstraction for messaging provider subscription services.
 * Allows UserService to be provider-agnostic and support multiple providers.
 * 
 * Implementations:
 * - WasenderSubscriptionServiceClient (calls whatsapp-worker REST API)
 * - Future: TwilioSubscriptionServiceClient, MetaCloudSubscriptionServiceClient, etc.
 */
public interface MessagingSubscriptionService {
    
    /**
     * Get subscription information for a given API key.
     * 
     * @param apiKey Provider API key
     * @return Subscription information
     * @throws IllegalArgumentException if API key is invalid
     */
    SubscriptionInfo getSubscriptionInfo(String apiKey);
    
    /**
     * Get the provider name (e.g., "WASender", "Twilio", "Meta Cloud API").
     * Used for logging and debugging.
     * 
     * @return Provider name
     */
    String getProviderName();
    
    /**
     * Subscription information returned by provider services.
     * Converted to enums at service boundaries for type safety.
     */
    class SubscriptionInfo {
        private String subscriptionType; // FREE_TRIAL, PAID
        private String subscriptionStatus; // ACTIVE, EXPIRED, CANCELLED
        private Integer sessionsAllowed;
        
        public SubscriptionInfo() {
        }
        
        public SubscriptionInfo(String subscriptionType, String subscriptionStatus, Integer sessionsAllowed) {
            this.subscriptionType = subscriptionType;
            this.subscriptionStatus = subscriptionStatus;
            this.sessionsAllowed = sessionsAllowed;
        }
        
        public String getSubscriptionType() {
            return subscriptionType;
        }
        
        public void setSubscriptionType(String subscriptionType) {
            this.subscriptionType = subscriptionType;
        }
        
        public String getSubscriptionStatus() {
            return subscriptionStatus;
        }
        
        public void setSubscriptionStatus(String subscriptionStatus) {
            this.subscriptionStatus = subscriptionStatus;
        }
        
        public Integer getSessionsAllowed() {
            return sessionsAllowed;
        }
        
        public void setSessionsAllowed(Integer sessionsAllowed) {
            this.sessionsAllowed = sessionsAllowed;
        }
    }
}

