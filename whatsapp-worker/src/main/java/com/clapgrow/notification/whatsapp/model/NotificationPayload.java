package com.clapgrow.notification.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Notification payload for Kafka messages consumed by whatsapp-worker.
 * 
 * ⚠️ KAFKA COMPATIBILITY: This payload structure changed to remove wasenderApiKey field.
 * This change requires coordinated deployment of producer (notification-api) and consumer
 * (whatsapp-worker) or a short dual-read window. The consumer now resolves API keys from
 * the database using whatsappSessionName or siteId references.
 * 
 * ⚠️ SECURITY: This payload does NOT contain API keys or other secrets.
 * API keys are resolved in the consumer using whatsappSessionName -> WhatsAppSession -> sessionApiKey
 * or siteId -> FrappeSite -> whatsappSessionName -> WhatsAppSession -> sessionApiKey.
 * This prevents credential leakage through Kafka logs, DLQs, or message replay.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationPayload {
    private String messageId;
    private UUID siteId;  // Changed from String to UUID to match notification-api payload
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
    // ⚠️ REMOVED: sendgridApiKey - Resolve from siteId -> FrappeSite in consumer
    // ⚠️ REMOVED: wasenderApiKey - Resolve from whatsappSessionName -> WhatsAppSession or siteId -> FrappeSite -> WhatsAppSession in consumer
    private Map<String, String> metadata;
}

