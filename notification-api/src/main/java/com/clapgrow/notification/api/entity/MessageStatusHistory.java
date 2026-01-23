package com.clapgrow.notification.api.entity;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.HistorySource;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only history table for message status changes.
 * Provides audit trail, retry timelines, and failure analysis.
 */
@Entity
@Table(name = "message_status_history", indexes = {
    @Index(name = "idx_message_status_history_message_id", columnList = "message_id"),
    @Index(name = "idx_message_status_history_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    @Column(name = "status", nullable = false, columnDefinition = "delivery_status")
    @org.hibernate.annotations.Type(com.clapgrow.notification.api.config.PostgreSQLDeliveryStatusType.class)
    private DeliveryStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /**
     * Source of the history entry.
     * - API: Created by MessageStatusHistoryService (emits metrics, validates transitions)
     * - TRIGGER: Created by database trigger (ensures no status change is missed)
     * - WORKER: Created by worker service (if needed in future)
     * 
     * Used for deduplication and audit trail.
     */
    @Column(name = "source", nullable = false, columnDefinition = "history_source")
    @org.hibernate.annotations.Type(com.clapgrow.notification.api.config.PostgreSQLHistorySourceType.class)
    private HistorySource source;

    public MessageStatusHistory(String messageId, DeliveryStatus status, String errorMessage, Integer retryCount) {
        this.messageId = messageId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.timestamp = LocalDateTime.now();
        this.source = HistorySource.API; // Default to API for application-created entries
    }
    
    public MessageStatusHistory(String messageId, DeliveryStatus status, String errorMessage, Integer retryCount, HistorySource source) {
        this.messageId = messageId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.retryCount = retryCount;
        this.timestamp = LocalDateTime.now();
        this.source = source;
    }
}

