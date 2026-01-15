package com.clapgrow.notification.api.integration;

import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.FrappeSiteRepository;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for MessageLogController.
 * Tests message log retrieval with database interactions.
 */
@AutoConfigureMockMvc
@Transactional
@DisplayName("Message Log Controller Integration Tests")
class MessageLogControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FrappeSiteRepository siteRepository;

    @Autowired
    private MessageLogRepository messageLogRepository;

    @Autowired
    private EntityManager entityManager;

    private FrappeSite testSite;
    private String testApiKey;

    @BeforeEach
    void setUp() {
        // Clean up test data
        // Handle case where tables might not exist yet (Hibernate DDL timing)
        // Use separate try-catch blocks to avoid transaction rollback issues
        cleanupIfTableExists("message_logs", () -> messageLogRepository.deleteAll());
        cleanupIfTableExists("frappe_sites", () -> siteRepository.deleteAll());

        // Create test site
        testApiKey = UUID.randomUUID().toString();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String apiKeyHash = encoder.encode(testApiKey);

        testSite = new FrappeSite();
        testSite.setSiteName("test-site-" + UUID.randomUUID().toString().substring(0, 8));
        testSite.setApiKey(testApiKey);
        testSite.setApiKeyHash(apiKeyHash);
        testSite.setIsActive(true);
        testSite = siteRepository.save(testSite);

        // Create test message logs
        createTestMessageLogs();
    }

    /**
     * Helper method to safely clean up data only if the table exists.
     * This prevents transaction rollback issues when tables don't exist yet.
     */
    private void cleanupIfTableExists(String tableName, Runnable cleanupAction) {
        try {
            // Check if table exists by querying it
            entityManager.createNativeQuery("SELECT 1 FROM " + tableName + " LIMIT 1").getResultList();
            // Table exists, proceed with cleanup
            cleanupAction.run();
        } catch (PersistenceException | InvalidDataAccessResourceUsageException e) {
            // Table doesn't exist yet - Hibernate will create it when we save
            // This can happen if setUp() runs before Hibernate completes DDL
            // Ignore and continue
        }
    }

    private void createTestMessageLogs() {
        for (int i = 0; i < 5; i++) {
            MessageLog log = new MessageLog();
            log.setMessageId(UUID.randomUUID().toString());
            log.setSiteId(testSite.getId());
            log.setChannel(NotificationChannel.EMAIL);
            log.setRecipient("user" + i + "@example.com");
            log.setSubject("Test Subject " + i);
            log.setBody("Test Body " + i);
            log.setStatus(i % 2 == 0 ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED);
            log.setSentAt(LocalDateTime.now().minusHours(i));
            log.setCreatedAt(LocalDateTime.now().minusHours(i));
            log.setRetryCount(0);
            messageLogRepository.save(log);
        }
    }

    @Test
    @DisplayName("Should retrieve message logs successfully")
    void testGetMessageLogs() throws Exception {
        mockMvc.perform(get("/api/v1/messages/logs")
                .header("X-Site-Key", testApiKey)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.messages[0].messageId").exists())
                .andExpect(jsonPath("$.messages[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    @DisplayName("Should filter message logs by status")
    void testGetMessageLogsByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/messages/logs")
                .header("X-Site-Key", testApiKey)
                .param("status", "DELIVERED")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.messages[*].status", everyItem(is("DELIVERED"))));
    }

    @Test
    @DisplayName("Should filter message logs by channel")
    void testGetMessageLogsByChannel() throws Exception {
        mockMvc.perform(get("/api/v1/messages/logs")
                .header("X-Site-Key", testApiKey)
                .param("channel", "EMAIL")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.messages[*].channel", everyItem(is("EMAIL"))));
    }

    @Test
    @DisplayName("Should reject request with invalid API key")
    void testGetMessageLogsWithInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/messages/logs")
                .header("X-Site-Key", "invalid-key")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return empty result for site with no messages")
    void testGetMessageLogsForEmptySite() throws Exception {
        // Create a new site with no messages
        String newApiKey = UUID.randomUUID().toString();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        FrappeSite newSite = new FrappeSite();
        newSite.setSiteName("empty-site");
        newSite.setApiKey(newApiKey);
        newSite.setApiKeyHash(encoder.encode(newApiKey));
        newSite.setIsActive(true);
        siteRepository.save(newSite);

        mockMvc.perform(get("/api/v1/messages/logs")
                .header("X-Site-Key", newApiKey)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Should support pagination")
    void testGetMessageLogsWithPagination() throws Exception {
        mockMvc.perform(get("/api/v1/messages/logs")
                .header("X-Site-Key", testApiKey)
                .param("page", "0")
                .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(greaterThan(1)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }
}

