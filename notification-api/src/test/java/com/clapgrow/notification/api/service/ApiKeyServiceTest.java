package com.clapgrow.notification.api.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiKeyServiceTest {
    
    private final ApiKeyService apiKeyService = new ApiKeyService();
    
    @Test
    void testGenerateApiKey() {
        String apiKey = apiKeyService.generateApiKey();
        assertNotNull(apiKey);
        assertFalse(apiKey.isEmpty());
        assertTrue(apiKey.length() > 0);
    }
    
    @Test
    void testGenerateUniqueApiKeys() {
        String key1 = apiKeyService.generateApiKey();
        String key2 = apiKeyService.generateApiKey();
        assertNotEquals(key1, key2);
    }
    
    @Test
    void testHashAndValidateApiKey() {
        String apiKey = apiKeyService.generateApiKey();
        String hash = apiKeyService.hashApiKey(apiKey);
        
        assertNotNull(hash);
        assertNotEquals(apiKey, hash);
        assertTrue(apiKeyService.validateApiKey(apiKey, hash));
        assertFalse(apiKeyService.validateApiKey("wrong-key", hash));
    }
}

