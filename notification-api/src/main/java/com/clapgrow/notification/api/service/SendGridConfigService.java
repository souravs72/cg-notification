package com.clapgrow.notification.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SendGridConfigService {
    
    public void saveApiKey(String apiKey, String emailFromAddress, String emailFromName) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("SendGrid API key cannot be empty");
        }
        // TODO: Implement SendGrid configuration storage
        log.info("SendGrid API key saved (stub implementation)");
    }
    
    public boolean isConfigured() {
        // TODO: Implement check for SendGrid configuration
        return false;
    }
}

