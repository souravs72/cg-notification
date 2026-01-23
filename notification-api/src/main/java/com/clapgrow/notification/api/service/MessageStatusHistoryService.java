package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.MessageStatusHistory;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.HistorySource;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import com.clapgrow.notification.api.repository.MessageStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing append-only message status history.
 * Provides audit trail, retry timelines, and failure analysis.
 * 
 * ⚠️ SINGLE SOURCE OF TRUTH: This service is the ONLY place where metrics are emitted
 * when status changes. This prevents double-counting failures.
 * 
 * ⚠️ INTENTIONAL DESIGN: History records "attempted reality"
 * 
 * When invalid transitions are detected, history is still appended (with error log).
 * This means:
 * - message_logs = source of truth (enforced by DB constraints and validators)
 * - message_status_history = attempted reality (records what was attempted, even if invalid)
 * 
 * This is intentional - history provides audit trail of all attempts, including invalid ones.
 * If audits get complex in the future, consider adding is_valid_transition BOOLEAN column.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageStatusHistoryService {
    
    private final MessageStatusHistoryRepository historyRepository;
    private final MessageLogRepository messageLogRepository;
    private final NotificationMetricsService metricsService;
    private final StatusTransitionValidator statusTransitionValidator;

    /**
     * Append a status change to the history.
     * This is append-only - never updates or deletes.
     * 
     * ⚠️ METRICS: This method is the SINGLE SOURCE OF TRUTH for emitting metrics
     * when status changes. All metrics are emitted here to prevent double-counting.
     * 
     * @param messageId Message ID
     * @param status New status
     * @param errorMessage Error message (if any)
     * @param retryCount Current retry count
     */
    @Transactional
    public void appendStatusChange(String messageId, DeliveryStatus status, String errorMessage, Integer retryCount) {
        try {
            // Validate status transition before appending to history
            Optional<com.clapgrow.notification.api.entity.MessageLog> messageLogOpt = 
                messageLogRepository.findByMessageId(messageId);
            
            if (messageLogOpt.isPresent()) {
                DeliveryStatus oldStatus = messageLogOpt.get().getStatus();
                
                // Validate transition (only if status actually changed)
                if (oldStatus != status) {
                    if (!statusTransitionValidator.isValidTransition(oldStatus, status)) {
                        log.error("Invalid status transition detected for message {}: {} → {}. " +
                            "Status history will still be appended for audit trail (attempted reality).", 
                            messageId, oldStatus, status);
                        // ⚠️ INTENTIONAL: History records attempted reality, even if invalid
                        // This provides audit trail of all attempts, including invalid transitions
                        // message_logs is the source of truth (enforced by DB constraints)
                    }
                }
            }
            
            MessageStatusHistory history = new MessageStatusHistory(
                messageId,
                status,
                errorMessage,
                retryCount != null ? retryCount : 0,
                HistorySource.API  // Mark as API-created (emits metrics, validates transitions)
            );
            historyRepository.save(history);
            log.debug("Appended status history for message {}: {}", messageId, status);
            
            // ⚠️ SINGLE SOURCE OF TRUTH: Emit metrics here when status changes
            // Fetch channel from MessageLog to emit metrics
            if (messageLogOpt.isPresent()) {
                NotificationChannel channel = messageLogOpt.get().getChannel();
                metricsService.recordStatusChange(channel, status, retryCount);
            } else {
                log.warn("MessageLog not found for messageId={}, cannot emit metrics", messageId);
            }
        } catch (Exception e) {
            // Don't fail the main operation if history append fails
            log.error("Failed to append status history for message {}", messageId, e);
        }
    }

    /**
     * Get complete status history for a message.
     * 
     * @param messageId Message ID
     * @return List of status changes ordered by timestamp
     */
    public List<MessageStatusHistory> getStatusHistory(String messageId) {
        return historyRepository.findByMessageIdOrderByTimestampAsc(messageId);
    }

    /**
     * Get latest status change for a message.
     * 
     * @param messageId Message ID
     * @return Latest status history entry or null
     */
    public MessageStatusHistory getLatestStatusChange(String messageId) {
        return historyRepository.findFirstByMessageIdOrderByTimestampDesc(messageId);
    }
}

