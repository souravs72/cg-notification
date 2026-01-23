package com.clapgrow.notification.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for subscription defaults.
 * 
 * Maps to:
 * subscription:
 *   defaults:
 *     free-trial-sessions: 3
 *     paid-sessions: 100
 */
@Configuration
@ConfigurationProperties(prefix = "subscription.defaults")
@Data
public class SubscriptionDefaultsProperties {
    
    /**
     * Default number of sessions allowed for free trial subscriptions.
     * Default: 3
     */
    private int freeTrialSessions = 3;
    
    /**
     * Default number of sessions allowed for paid subscriptions.
     * Default: 100
     */
    private int paidSessions = 100;
}





