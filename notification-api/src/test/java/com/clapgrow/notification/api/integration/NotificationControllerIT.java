package com.clapgrow.notification.api.integration;

import com.clapgrow.notification.api.dto.BulkNotificationRequest;
import com.clapgrow.notification.api.dto.NotificationRequest;
import com.clapgrow.notification.api.dto.ScheduledNotificationRequest;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.FrappeSiteRepository;
import com.clapgrow.notification.api.service.SiteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for NotificationController.
 * Tests the full request/response cycle including database and Kafka interactions.
 */
@AutoConfigureMockMvc
@Transactional
@DisplayName("Notification Controller Integration Tests")
class NotificationControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FrappeSiteRepository siteRepository;

    @Autowired
    private SiteService siteService;

    private FrappeSite testSite;
    private String testApiKey;

    @BeforeEach
    void setUp() {
        // Clean up any existing test data
        siteRepository.deleteAll();

        // Create a test site with API key
        testApiKey = UUID.randomUUID().toString();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String apiKeyHash = encoder.encode(testApiKey);

        testSite = new FrappeSite();
        testSite.setSiteName("test-site-" + UUID.randomUUID().toString().substring(0, 8));
        testSite.setApiKey(testApiKey);
        testSite.setApiKeyHash(apiKeyHash);
        testSite.setIsActive(true);
        testSite.setDescription("Test site for integration tests");
        testSite = siteRepository.save(testSite);
    }

    @Test
    @DisplayName("Should send a single email notification successfully")
    void testSendEmailNotification() throws Exception {
        NotificationRequest request = new NotificationRequest();
        request.setChannel(NotificationChannel.EMAIL);
        request.setRecipient("test@example.com");
        request.setSubject("Test Email");
        request.setBody("This is a test email body");
        request.setFromEmail("sender@example.com");
        request.setFromName("Test Sender");
        request.setIsHtml(false);

        mockMvc.perform(post("/api/v1/notifications/send")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));
    }

    @Test
    @DisplayName("Should send a single WhatsApp notification successfully")
    void testSendWhatsAppNotification() throws Exception {
        NotificationRequest request = new NotificationRequest();
        request.setChannel(NotificationChannel.WHATSAPP);
        request.setRecipient("+1234567890");
        request.setBody("This is a test WhatsApp message");
        request.setCaption("Test Caption");

        mockMvc.perform(post("/api/v1/notifications/send")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.channel").value("WHATSAPP"));
    }

    @Test
    @DisplayName("Should reject notification with invalid API key")
    void testSendNotificationWithInvalidApiKey() throws Exception {
        NotificationRequest request = new NotificationRequest();
        request.setChannel(NotificationChannel.EMAIL);
        request.setRecipient("test@example.com");
        request.setBody("Test body");

        mockMvc.perform(post("/api/v1/notifications/send")
                .header("X-Site-Key", "invalid-api-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject notification with missing required fields")
    void testSendNotificationWithMissingFields() throws Exception {
        NotificationRequest request = new NotificationRequest();
        // Missing channel and recipient

        mockMvc.perform(post("/api/v1/notifications/send")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should send bulk notifications successfully")
    void testSendBulkNotifications() throws Exception {
        NotificationRequest request1 = new NotificationRequest();
        request1.setChannel(NotificationChannel.EMAIL);
        request1.setRecipient("user1@example.com");
        request1.setSubject("Bulk Test 1");
        request1.setBody("Body 1");

        NotificationRequest request2 = new NotificationRequest();
        request2.setChannel(NotificationChannel.EMAIL);
        request2.setRecipient("user2@example.com");
        request2.setSubject("Bulk Test 2");
        request2.setBody("Body 2");

        BulkNotificationRequest bulkRequest = new BulkNotificationRequest();
        bulkRequest.setNotifications(Arrays.asList(request1, request2));

        mockMvc.perform(post("/api/v1/notifications/send/bulk")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.totalRequested").value(2))
                .andExpect(jsonPath("$.totalAccepted").value(2))
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].messageId").exists())
                .andExpect(jsonPath("$.results[1].messageId").exists());
    }

    @Test
    @DisplayName("Should schedule a notification successfully")
    void testScheduleNotification() throws Exception {
        ScheduledNotificationRequest request = new ScheduledNotificationRequest();
        request.setChannel(NotificationChannel.EMAIL);
        request.setRecipient("test@example.com");
        request.setSubject("Scheduled Email");
        request.setBody("This email is scheduled");
        request.setScheduledAt(LocalDateTime.now().plusHours(1));

        mockMvc.perform(post("/api/v1/notifications/schedule")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").exists())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @DisplayName("Should reject scheduled notification with past date")
    void testScheduleNotificationWithPastDate() throws Exception {
        ScheduledNotificationRequest request = new ScheduledNotificationRequest();
        request.setChannel(NotificationChannel.EMAIL);
        request.setRecipient("test@example.com");
        request.setBody("Test body");
        request.setScheduledAt(LocalDateTime.now().minusHours(1)); // Past date

        mockMvc.perform(post("/api/v1/notifications/schedule")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty bulk notification request")
    void testSendEmptyBulkNotifications() throws Exception {
        BulkNotificationRequest bulkRequest = new BulkNotificationRequest();
        bulkRequest.setNotifications(List.of());

        mockMvc.perform(post("/api/v1/notifications/send/bulk")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bulkRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle notification with metadata")
    void testSendNotificationWithMetadata() throws Exception {
        NotificationRequest request = new NotificationRequest();
        request.setChannel(NotificationChannel.EMAIL);
        request.setRecipient("test@example.com");
        request.setSubject("Test with Metadata");
        request.setBody("Body with metadata");
        request.setMetadata(java.util.Map.of(
            "campaignId", "campaign-123",
            "userId", "user-456"
        ));

        mockMvc.perform(post("/api/v1/notifications/send")
                .header("X-Site-Key", testApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").exists());
    }
}

