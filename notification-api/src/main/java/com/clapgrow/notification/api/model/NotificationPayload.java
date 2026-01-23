package com.clapgrow.notification.api.model;

import com.clapgrow.notification.api.enums.NotificationChannel;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * Notification payload for Kafka messages.
 * 
 * ⚠️ SECURITY: This payload does NOT contain API keys or other secrets.
 * API keys are resolved in the consumer using siteId/whatsappSessionName references.
 * This prevents credential leakage through Kafka logs, DLQs, or message replay.
 * 
 * Design principles:
 * - Minimal: Only transport data, not credentials or rendering hints
 * - Type-safe: Uses enums and proper types (UUID, NotificationChannel)
 * - Immutable-friendly: Simple data transfer object
 * 
 * Future hardening options (optional, not urgent):
 * - Consider @Builder pattern with @Getter/@AllArgsConstructor for immutability guarantees
 * - Consider typed metadata (Map<String, Object>) if complex structures needed (currently Map<String, String> is safer for Kafka)
 */
@Data
public class NotificationPayload {
    private String messageId;
    private UUID siteId;  // Reference to site, used to resolve API keys in consumer
    private NotificationChannel channel;  // Type-safe enum instead of String
    private String recipient;
    private String subject;
    private String body;
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    // EMAIL fields
    private String fromEmail;
    private String fromName;
    /**
     * Whether the message body is HTML.
     * null is treated as false by the consumer.
     * Using Boolean (not boolean) for backward compatibility with multiple producers.
     */
    private Boolean isHtml;
    
    // WHATSAPP fields
    private String whatsappSessionName;  // Reference to WhatsApp session, used to resolve Wasender API key
    private String caption;  // Caption for media messages (image/video/document)
    // ⚠️ REMOVED: wasenderApiKey - Resolve from whatsappSessionName -> WhatsAppSession or siteId -> FrappeSite -> WhatsAppSession in consumer
    // ⚠️ REMOVED: sendgridApiKey - Resolve from siteId -> FrappeSite in consumer
    private Map<String, String> metadata;
}

