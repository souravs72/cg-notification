package com.clapgrow.notification.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminAuthService {
    
    private final String configuredAdminKey;
    
    public AdminAuthService(@Value("${admin.api-key:}") String adminApiKey) {
        if (adminApiKey != null && !adminApiKey.trim().isEmpty()) {
            this.configuredAdminKey = adminApiKey;
        } else {
            this.configuredAdminKey = null;
            log.warn("Admin API key is not configured. Admin endpoints will be inaccessible.");
        }
    }
    
    public void validateAdminKey(String providedKey) {
        if (configuredAdminKey == null) {
            throw new SecurityException("Admin API key is not configured");
        }
        
        if (providedKey == null || providedKey.trim().isEmpty()) {
            throw new SecurityException("Admin API key is required");
        }
        
        if (!constantTimeEquals(providedKey, configuredAdminKey)) {
            throw new SecurityException("Invalid admin API key");
        }
    }
    
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        int maxLength = Math.max(a.length(), b.length());
        int result = 0;
        for (int i = 0; i < maxLength; i++) {
            char charA = (i < a.length()) ? a.charAt(i) : 0;
            char charB = (i < b.length()) ? b.charAt(i) : 0;
            result |= charA ^ charB;
        }
        return result == 0 && a.length() == b.length();
    }
}

