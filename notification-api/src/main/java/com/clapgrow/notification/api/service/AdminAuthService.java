package com.clapgrow.notification.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AdminAuthService {
    
    public void validateAdminKey(String adminKey) {
        if (adminKey == null || adminKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin key cannot be empty");
        }
        // TODO: Implement admin key validation
        log.debug("Admin key validated (stub implementation)");
    }
}

