package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.WasenderConfig;
import com.clapgrow.notification.api.repository.WasenderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WasenderConfigServiceTest {

    @Mock
    private WasenderConfigRepository configRepository;

    @InjectMocks
    private WasenderConfigService wasenderConfigService;

    private WasenderConfig existingConfig;
    private String testApiKey;

    @BeforeEach
    void setUp() {
        testApiKey = "test-api-key-12345";
        existingConfig = new WasenderConfig();
        existingConfig.setId(UUID.randomUUID());
        existingConfig.setWasenderApiKey("old-api-key");
        existingConfig.setIsDeleted(false);
    }

    @Test
    void testGetApiKey_WhenConfigured_ReturnsApiKey() {
        when(configRepository.findByIsDeletedFalse()).thenReturn(Optional.of(existingConfig));

        Optional<String> result = wasenderConfigService.getApiKey();

        assertTrue(result.isPresent());
        assertEquals("old-api-key", result.get());
    }

    @Test
    void testGetApiKey_WhenNotConfigured_ReturnsEmpty() {
        when(configRepository.findByIsDeletedFalse()).thenReturn(Optional.empty());

        Optional<String> result = wasenderConfigService.getApiKey();

        assertFalse(result.isPresent());
    }

    @Test
    void testIsConfigured_WhenConfigured_ReturnsTrue() {
        when(configRepository.existsByIsDeletedFalse()).thenReturn(true);

        boolean result = wasenderConfigService.isConfigured();

        assertTrue(result);
    }

    @Test
    void testIsConfigured_WhenNotConfigured_ReturnsFalse() {
        when(configRepository.existsByIsDeletedFalse()).thenReturn(false);

        boolean result = wasenderConfigService.isConfigured();

        assertFalse(result);
    }

    @Test
    void testSaveApiKey_WhenNoExistingConfig_CreatesNew() {
        when(configRepository.findByIsDeletedFalse()).thenReturn(Optional.empty());
        when(configRepository.save(any(WasenderConfig.class))).thenAnswer(invocation -> {
            WasenderConfig config = invocation.getArgument(0);
            config.setId(UUID.randomUUID());
            return config;
        });

        wasenderConfigService.saveApiKey(testApiKey);

        verify(configRepository).save(any(WasenderConfig.class));
        verify(configRepository, never()).save(existingConfig);
    }

    @Test
    void testSaveApiKey_WhenExistingConfig_SoftDeletesAndCreatesNew() {
        when(configRepository.findByIsDeletedFalse()).thenReturn(Optional.of(existingConfig));
        when(configRepository.save(any(WasenderConfig.class))).thenAnswer(invocation -> {
            WasenderConfig config = invocation.getArgument(0);
            if (config.getId() == null) {
                config.setId(UUID.randomUUID());
            }
            return config;
        });

        wasenderConfigService.saveApiKey(testApiKey);

        verify(configRepository).save(existingConfig); // Soft delete existing
        verify(configRepository, times(2)).save(any(WasenderConfig.class)); // Delete + create
        assertTrue(existingConfig.getIsDeleted());
    }

    @Test
    void testSaveApiKey_WhenApiKeyIsEmpty_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            wasenderConfigService.saveApiKey("");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            wasenderConfigService.saveApiKey(null);
        });

        verify(configRepository, never()).save(any());
    }

    @Test
    void testUpdateApiKey_WhenConfigured_UpdatesExisting() {
        when(configRepository.findByIsDeletedFalse()).thenReturn(Optional.of(existingConfig));
        when(configRepository.save(any(WasenderConfig.class))).thenReturn(existingConfig);

        wasenderConfigService.updateApiKey(testApiKey);

        verify(configRepository).save(existingConfig);
        assertEquals(testApiKey.trim(), existingConfig.getWasenderApiKey());
    }

    @Test
    void testUpdateApiKey_WhenNotConfigured_ThrowsException() {
        when(configRepository.findByIsDeletedFalse()).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> {
            wasenderConfigService.updateApiKey(testApiKey);
        });
    }
}














