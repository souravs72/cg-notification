package com.clapgrow.notification.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Admin authentication service using API key validation.
 * Uses MessageDigest.isEqual for constant-time comparison to prevent timing attacks.
 */
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
    
    /**
     * Constant-time string comparison using MessageDigest.isEqual.
     * This prevents timing attacks by ensuring comparison always takes the same time.
     * 
     * @param a First string to compare
     * @param b Second string to compare
     * @return true if strings are equal, false otherwise
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8)
        );
    }
}

