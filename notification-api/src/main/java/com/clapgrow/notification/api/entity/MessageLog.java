package com.clapgrow.notification.api.entity;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "message_logs", indexes = {
    @Index(name = "idx_site_id", columnList = "site_id"),
    @Index(name = "idx_channel", columnList = "channel"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageLog extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true, length = 100)
    private String messageId;

    @Column(name = "site_id", nullable = true)
    private UUID siteId;

    @Column(name = "channel", nullable = false, columnDefinition = "notification_channel")
    @org.hibernate.annotations.Type(com.clapgrow.notification.api.config.PostgreSQLNotificationChannelType.class)
    private NotificationChannel channel;

    /**
     * Current delivery status.
     * 
     * Status history is automatically appended to message_status_history table
     * when status changes. See MessageStatusHistoryService for details.
     * 
     * Benefits: Retry timelines, failure analysis, compliance audit trails
     */
    @Column(name = "status", nullable = false, columnDefinition = "delivery_status")
    @org.hibernate.annotations.Type(com.clapgrow.notification.api.config.PostgreSQLDeliveryStatusType.class)
    private DeliveryStatus status;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Type of failure (KAFKA or CONSUMER).
     * Only set when status is FAILED.
     * Used to efficiently query retry candidates without filtering in Java.
     */
    @Column(name = "failure_type", columnDefinition = "failure_type")
    @org.hibernate.annotations.Type(com.clapgrow.notification.api.config.PostgreSQLFailureTypeType.class)
    private com.clapgrow.notification.api.enums.FailureType failureType;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "metadata", columnDefinition = "JSONB")
    @org.hibernate.annotations.Type(com.clapgrow.notification.api.config.PostgreSQLJSONBType.class)
    private String metadata;

    // WhatsApp specific fields
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "video_url", length = 1000)
    private String videoUrl;

    @Column(name = "document_url", length = 1000)
    private String documentUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    // Email specific fields
    @Column(name = "from_email", length = 255)
    private String fromEmail;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(name = "is_html")
    private Boolean isHtml = false;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
}

