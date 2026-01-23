package com.clapgrow.notification.email.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Notification payload for Kafka messages consumed by email-worker.
 * 
 * ⚠️ KAFKA COMPATIBILITY: This payload structure matches notification-api model.
 * siteId is UUID (not String) to match the API payload structure.
 * @JsonAlias("site_id") ensures binding even if producer sends snake_case.
 * 
 * ⚠️ SECURITY: This payload does NOT contain API keys or other secrets.
 * API keys are resolved in the consumer using siteId -> FrappeSite -> sendgridApiKey
 * or global SendGridConfig. This prevents credential leakage through Kafka logs, DLQs, or message replay.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationPayload {
    private String messageId;
    @JsonAlias("site_id")
    private UUID siteId;  // API sends "siteId"; alias supports "site_id" for robustness
    private String channel;
    private String recipient;
    private String subject;
    private String body;
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    private String caption;
    private String fromEmail;
    private String fromName;
    private Boolean isHtml;
    private String whatsappSessionName;
    // ⚠️ REMOVED: sendgridApiKey - Resolve from siteId -> FrappeSite or global SendGridConfig in consumer
    private Map<String, String> metadata;
}

