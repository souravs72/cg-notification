package com.clapgrow.notification.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for encryption of sensitive fields at rest.
 * Initializes the EncryptedStringAttributeConverter with encryption key.
 */
@Configuration
@Slf4j
public class EncryptionConfig {
    
    @Value("${encryption.enabled:false}")
    private boolean encryptionEnabled;
    
    @Value("${encryption.key:}")
    private String encryptionKey;
    
    @PostConstruct
    public void initializeEncryption() {
        EncryptedStringAttributeConverter.initialize(encryptionKey, encryptionEnabled);
        
        if (encryptionEnabled && !encryptionKey.isEmpty()) {
            log.info("Encryption enabled for sensitive fields");
        } else if (encryptionEnabled) {
            log.warn("Encryption enabled but no key provided - encryption will be disabled");
        } else {
            log.info("Encryption disabled - sensitive fields stored in plaintext");
        }
    }
    
    /**
     * Generate a new encryption key.
     * This method can be called during setup to generate the initial key.
     */
    public static String generateNewKey() {
        return EncryptedStringAttributeConverter.generateEncryptionKey();
    }
}

