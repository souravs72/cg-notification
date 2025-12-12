package com.clapgrow.notification.api.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyService {
    
    private static final int API_KEY_LENGTH = 64;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public ApiKeyService() {
        this.passwordEncoder = new BCryptPasswordEncoder(12);
        this.secureRandom = new SecureRandom();
    }

    public String generateApiKey() {
        byte[] randomBytes = new byte[API_KEY_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashApiKey(String apiKey) {
        return passwordEncoder.encode(apiKey);
    }

    public boolean validateApiKey(String rawApiKey, String hashedApiKey) {
        return passwordEncoder.matches(rawApiKey, hashedApiKey);
    }
}

