package com.clapgrow.notification.api.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter for encrypting/decrypting sensitive strings at rest.
 * 
 * ⚠️ SECURITY NOTE: This is a basic implementation using AES encryption.
 * For production, consider:
 * - Using a Key Management Service (KMS) like AWS KMS, Azure Key Vault, or HashiCorp Vault
 * - Storing encryption keys securely (not in application properties)
 * - Using envelope encryption for better key management
 * - Rotating encryption keys periodically
 * 
 * Current implementation uses a symmetric key from application properties.
 * Key rotation requires decrypting all existing values with old key and re-encrypting with new key.
 * 
 * To enable encryption, set: encryption.enabled=true and encryption.key=<base64-key>
 * To generate a key: EncryptedStringAttributeConverter.generateEncryptionKey()
 */
@Converter
@Slf4j
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {
    
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    
    private static SecretKey secretKey;
    private static boolean encryptionEnabled = false;
    
    /**
     * Initialize the converter with encryption key from configuration.
     * This is called by EncryptionConfig during Spring startup.
     */
    public static void initialize(String encryptionKey, boolean enabled) {
        encryptionEnabled = enabled;
        
        if (!enabled) {
            log.info("Encryption is disabled - API keys will be stored in plaintext");
            return;
        }
        
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            log.warn("Encryption enabled but no key provided - falling back to plaintext storage");
            encryptionEnabled = false;
            return;
        }
        
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKey);
            secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            log.info("Encryption initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize encryption key", e);
            encryptionEnabled = false;
        }
    }
    
    /**
     * Generate a new encryption key (for initial setup).
     * This should be run once to generate the key, then stored securely.
     * 
     * @return Base64-encoded encryption key
     */
    public static String generateEncryptionKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || !encryptionEnabled || secretKey == null) {
            return attribute; // Return plaintext if encryption disabled
        }
        
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Failed to encrypt attribute value", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || !encryptionEnabled || secretKey == null) {
            return dbData; // Return as-is if encryption disabled
        }
        
        try {
            // Check if value is encrypted (base64-encoded encrypted data)
            // If decryption fails, assume it's plaintext (for backward compatibility during migration)
            try {
                Cipher cipher = Cipher.getInstance(ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(dbData));
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // If decryption fails, it might be plaintext (during migration)
                log.debug("Decryption failed, assuming plaintext value: {}", e.getMessage());
                return dbData;
            }
        } catch (Exception e) {
            log.error("Failed to decrypt attribute value", e);
            // Return as-is for backward compatibility
            return dbData;
        }
    }
}


