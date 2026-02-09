package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.WasenderConfig;
import com.clapgrow.notification.api.repository.WasenderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderConfigService {
    
    private final WasenderConfigRepository configRepository;

    /**
     * Get the active WASender API key
     * @return The API key if configured, empty otherwise
     */
    public Optional<String> getApiKey() {
        return configRepository.findByIsDeletedFalse()
            .map(WasenderConfig::getWasenderApiKey);
    }

    /**
     * Check if WASender API key is configured
     */
    public boolean isConfigured() {
        return configRepository.existsByIsDeletedFalse();
    }

    /**
     * Save or update the WASender API key
     * If an active config exists, it will be soft-deleted and a new one created
     * @param apiKey The WASender API key
     */
    @Transactional
    public void saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("WASender API key cannot be empty");
        }

        // Soft delete existing active config
        configRepository.findByIsDeletedFalse().ifPresent(existing -> {
            existing.setIsDeleted(true);
            existing.setUpdatedBy("SYSTEM");
            configRepository.save(existing);
            log.info("Soft-deleted existing WASender config with ID: {}", existing.getId());
        });

        // Create new config
        WasenderConfig config = new WasenderConfig();
        config.setWasenderApiKey(apiKey.trim());
        config.setCreatedBy("SYSTEM");
        config.setIsDeleted(false);
        
        config = configRepository.save(config);
        log.info("Saved new WASender API key configuration with ID: {}", config.getId());
    }

    /**
     * Update the existing active API key
     * @param apiKey The new WASender API key
     */
    @Transactional
    public void updateApiKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("WASender API key cannot be empty");
        }

        WasenderConfig config = configRepository.findByIsDeletedFalse()
            .orElseThrow(() -> new IllegalStateException("No active WASender configuration found. Please create one first."));

        config.setWasenderApiKey(apiKey.trim());
        config.setUpdatedBy("SYSTEM");
        configRepository.save(config);
        log.info("Updated WASender API key configuration with ID: {}", config.getId());
    }
}
