package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.SendGridConfig;
import com.clapgrow.notification.api.repository.SendGridConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SendGridConfigService {
    
    private final SendGridConfigRepository configRepository;

    /**
     * Get the active SendGrid API key
     * @return The API key if configured, empty otherwise
     */
    public Optional<String> getApiKey() {
        return configRepository.findByIsDeletedFalse()
            .map(SendGridConfig::getSendgridApiKey);
    }

    /**
     * Get the active SendGrid configuration
     * @return The config if configured, empty otherwise
     */
    public Optional<SendGridConfig> getConfig() {
        return configRepository.findByIsDeletedFalse();
    }

    /**
     * Get the default email from address
     * @return The email from address if configured, empty otherwise
     */
    public Optional<String> getEmailFromAddress() {
        return configRepository.findByIsDeletedFalse()
            .map(SendGridConfig::getEmailFromAddress)
            .filter(addr -> addr != null && !addr.trim().isEmpty());
    }

    /**
     * Get the default email from name
     * @return The email from name if configured, empty otherwise
     */
    public Optional<String> getEmailFromName() {
        return configRepository.findByIsDeletedFalse()
            .map(SendGridConfig::getEmailFromName)
            .filter(name -> name != null && !name.trim().isEmpty());
    }

    /**
     * Check if SendGrid API key is configured
     */
    public boolean isConfigured() {
        return configRepository.existsByIsDeletedFalse();
    }

    /**
     * Save or update the SendGrid API key and email configuration
     * If an active config exists, it will be soft-deleted and a new one created
     * @param apiKey The SendGrid API key
     * @param emailFromAddress The default email from address
     * @param emailFromName The default email from name
     */
    @Transactional
    public void saveApiKey(String apiKey, String emailFromAddress, String emailFromName) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("SendGrid API key cannot be empty");
        }

        // Soft delete existing active config
        configRepository.findByIsDeletedFalse().ifPresent(existing -> {
            existing.setIsDeleted(true);
            existing.setUpdatedBy("SYSTEM");
            configRepository.save(existing);
            log.info("Soft-deleted existing SendGrid config with ID: {}", existing.getId());
        });

        // Create new config
        SendGridConfig config = new SendGridConfig();
        config.setSendgridApiKey(apiKey.trim());
        if (emailFromAddress != null && !emailFromAddress.trim().isEmpty()) {
            config.setEmailFromAddress(emailFromAddress.trim());
        }
        if (emailFromName != null && !emailFromName.trim().isEmpty()) {
            config.setEmailFromName(emailFromName.trim());
        }
        config.setCreatedBy("SYSTEM");
        config.setIsDeleted(false);
        
        config = configRepository.save(config);
        log.info("Saved new SendGrid API key configuration with ID: {}", config.getId());
    }

    /**
     * Update the existing active API key
     * @param apiKey The new SendGrid API key
     */
    @Transactional
    public void updateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("SendGrid API key cannot be empty");
        }

        SendGridConfig config = configRepository.findByIsDeletedFalse()
            .orElseThrow(() -> new IllegalStateException("No active SendGrid configuration found. Please create one first."));

        config.setSendgridApiKey(apiKey.trim());
        config.setUpdatedBy("SYSTEM");
        configRepository.save(config);
        log.info("Updated SendGrid API key configuration with ID: {}", config.getId());
    }
}



