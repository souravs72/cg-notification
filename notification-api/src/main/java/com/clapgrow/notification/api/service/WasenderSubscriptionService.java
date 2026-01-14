package com.clapgrow.notification.api.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WasenderSubscriptionService {
    
    public SubscriptionInfo getSubscriptionInfo(String apiKey) {
        // TODO: Implement WASender subscription info retrieval
        SubscriptionInfo info = new SubscriptionInfo();
        info.setSubscriptionType("FREE");
        info.setSubscriptionStatus("ACTIVE");
        info.setSessionsAllowed(1);
        return info;
    }
    
    @Data
    public static class SubscriptionInfo {
        private String subscriptionType;
        private String subscriptionStatus;
        private Integer sessionsAllowed;
    }
}

