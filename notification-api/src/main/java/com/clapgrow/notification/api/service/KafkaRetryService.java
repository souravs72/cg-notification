package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for retrying Kafka message publishing failures.
 * Processes messages that failed to publish to Kafka and retries them.
 * 
 * ⚠️ RETRY COUNT OWNERSHIP: This service is the ONLY place that mutates retry_count.
 * 
 * Hard rule: Only KafkaRetryService mutates retry_count - everywhere.
 * 
 * Consumers (email-worker, whatsapp-worker) should:
 * - Only set FAILED status
 * - Never increment retry_count
 * 
 * This ensures:
 * - Single source of truth for retry logic
 * - Consistent retry count tracking
 * - No drift or double-counting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaRetryService {
    
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MINUTES = 5; // Wait 5 minutes before first retry
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Value("${spring.kafka.topics.email:notifications-email}")
    private String emailTopic;
    
    @Value("${spring.kafka.topics.whatsapp:notifications-whatsapp}")
    private String whatsappTopic;
    
    @Value("${spring.kafka.topics.email-dlq:notifications-email-dlq}")
    private String emailDlqTopic;
    
    @Value("${spring.kafka.topics.whatsapp-dlq:notifications-whatsapp-dlq}")
    private String whatsappDlqTopic;
    
    /** Self-reference for proxy invocation. Required so @Transactional(REQUIRES_NEW) takes effect when
     * calling retrySingleMessageInNewTransaction from within the same class (avoids self-invocation bypass). */
    @Lazy
    @Autowired
    private KafkaRetryService self;

    private final MessageLogRepository messageLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final NotificationService notificationService;
    private final MessageStatusHistoryService messageStatusHistoryService;
    private final NotificationMetricsService metricsService;

    private static final int RETRY_BATCH_SIZE = 50; // Reduced from 100 to lower heap pressure (OOM at 2-3GB with large backlogs)
    
    /**
     * Retry failed Kafka publishes (producer-side failures).
     * Runs every 5 minutes to retry messages that failed to publish to Kafka.
     * Only retries messages that:
     * - Have status FAILED
     * - Failure type is KAFKA
     * - Retry count is less than MAX_RETRIES
     * - Were created more than RETRY_DELAY_MINUTES ago
     * 
     * ⚠️ BACKPRESSURE: Processes messages in batches of RETRY_BATCH_SIZE to prevent
     * overwhelming the system when there are many failures. Loops until no more messages found.
     * 
     * ⚠️ IMPORTANT: This method is NOT transactional. Each retry runs in its own REQUIRES_NEW transaction
     * to prevent cross-message rollbacks and ensure proper isolation.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void retryFailedKafkaPublishes() {
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
        int totalProcessed = 0;
        
        // Process in batches until no more messages found (backpressure)
        while (true) {
            // Find messages that failed due to Kafka errors and are eligible for retry
            // Query uses failure_type column for efficient database-level filtering
            List<MessageLog> failedMessages = messageLogRepository.findFailedMessagesForRetry(
                DeliveryStatus.FAILED,
                com.clapgrow.notification.api.enums.FailureType.KAFKA,
                MAX_RETRIES,
                retryThreshold,
                org.springframework.data.domain.PageRequest.of(0, RETRY_BATCH_SIZE)
            );
            
            if (failedMessages.isEmpty()) {
                if (totalProcessed > 0) {
                    log.info("Completed Kafka retry batch processing. Total processed: {}", totalProcessed);
                } else {
                    log.debug("No messages eligible for Kafka retry");
                }
                break;
            }
            
            log.info("Found {} messages eligible for Kafka retry (batch)", failedMessages.size());
            
            // Process each message in its own transaction to prevent cross-message rollbacks
            for (MessageLog messageLog : failedMessages) {
                Integer currentRetryCount = messageLog.getRetryCount() != null ? messageLog.getRetryCount() : 0;
                
                // Check if max retries exceeded before attempting retry
                if (currentRetryCount >= MAX_RETRIES) {
                    log.warn("Message {} has exceeded max retries ({}), sending to DLQ", 
                        messageLog.getMessageId(), currentRetryCount);
                    self.sendToDeadLetterTopicInNewTransaction(messageLog.getId());
                    continue;
                }
                
                try {
                    // Process in new transaction - isolated from other retries (use self to go through proxy)
                    self.retrySingleMessageInNewTransaction(messageLog.getId());
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error retrying Kafka publish for message {}", messageLog.getMessageId(), e);
                    // Error is logged, continue with next message
                }
            }
            
            // If we got fewer messages than batch size, we're done
            if (failedMessages.size() < RETRY_BATCH_SIZE) {
                if (totalProcessed > 0) {
                    log.info("Completed Kafka retry batch processing. Total processed: {}", totalProcessed);
                }
                break;
            }
        }
    }
    
    /**
     * Retry failed consumer processing (consumer-side failures).
     * 
     * ⚠️ SINGLE RETRY AUTHORITY: This is the ONLY retry mechanism for consumer failures.
     * Consumers fail fast and mark messages as FAILED. This service handles all retries.
     * 
     * Runs every 5 minutes to retry messages that failed during consumer processing.
     * Only retries messages that:
     * - Have status FAILED
     * - Failure type is CONSUMER
     * - Retry count is less than MAX_RETRIES
     * - Were created more than RETRY_DELAY_MINUTES ago
     * 
     * Benefits:
     * - Single retry authority reduces complexity
     * - No Thread.sleep() blocking consumer threads
     * - Consistent retry logic across all channels
     * - Better throughput and resource utilization
     * - Efficient database-level filtering using failure_type column
     * 
     * ⚠️ BACKPRESSURE: Processes messages in batches of RETRY_BATCH_SIZE to prevent
     * overwhelming the system when there are many failures. Loops until no more messages found.
     * 
     * ⚠️ IMPORTANT: This method is NOT transactional. Each retry runs in its own REQUIRES_NEW transaction
     * to prevent cross-message rollbacks and ensure proper isolation.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    public void retryFailedConsumerProcessing() {
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
        int totalProcessed = 0;
        
        // Process in batches until no more messages found (backpressure)
        while (true) {
            // Find messages that failed during consumer processing (not Kafka publish failures)
            // Query uses failure_type column for efficient database-level filtering
            List<MessageLog> failedMessages = messageLogRepository.findFailedMessagesForRetry(
                DeliveryStatus.FAILED,
                com.clapgrow.notification.api.enums.FailureType.CONSUMER,
                MAX_RETRIES,
                retryThreshold,
                org.springframework.data.domain.PageRequest.of(0, RETRY_BATCH_SIZE)
            );
            
            if (failedMessages.isEmpty()) {
                if (totalProcessed > 0) {
                    log.info("Completed consumer retry batch processing. Total processed: {}", totalProcessed);
                } else {
                    log.debug("No messages eligible for consumer retry");
                }
                break;
            }
            
            log.info("Found {} messages eligible for consumer retry (batch)", failedMessages.size());
            
            // Process each message in its own transaction to prevent cross-message rollbacks
            for (MessageLog messageLog : failedMessages) {
                Integer currentRetryCount = messageLog.getRetryCount() != null ? messageLog.getRetryCount() : 0;
                
                // Check if max retries exceeded before attempting retry
                if (currentRetryCount >= MAX_RETRIES) {
                    log.warn("Message {} has exceeded max retries ({}), sending to DLQ", 
                        messageLog.getMessageId(), currentRetryCount);
                    self.sendToDeadLetterTopicInNewTransaction(messageLog.getId());
                    continue;
                }
                
                try {
                    // Process in new transaction - isolated from other retries (use self to go through proxy)
                    self.retrySingleConsumerMessageInNewTransaction(messageLog.getId());
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error retrying consumer processing for message {}", messageLog.getMessageId(), e);
                    // Error is logged, continue with next message
                }
            }
            
            // If we got fewer messages than batch size, we're done
            if (failedMessages.size() < RETRY_BATCH_SIZE) {
                if (totalProcessed > 0) {
                    log.info("Completed consumer retry batch processing. Total processed: {}", totalProcessed);
                }
                break;
            }
        }
    }
    
    /**
     * Retry a single consumer-failed message by re-queuing it to Kafka.
     * Similar to retrySingleMessageInNewTransaction but specifically for consumer failures.
     * 
     * @param messageLogId Message log ID (not messageId) to retry
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void retrySingleConsumerMessageInNewTransaction(UUID messageLogId) {
        // Same logic as retrySingleMessageInNewTransaction - re-queue to Kafka topic
        retrySingleMessageInNewTransaction(messageLogId);
    }
    
    /**
     * Retry a single message in a new transaction with proper isolation.
     * Uses atomic claim to prevent concurrent retries by multiple scheduler instances.
     * Kafka send happens AFTER DB commit using TransactionSynchronizationManager.
     * 
     * @param messageLogId Message log ID (not messageId) to retry
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void retrySingleMessageInNewTransaction(UUID messageLogId) {
        // First, get the messageId without loading the full entity
        // This avoids potential entity manager state issues with @Modifying queries
        MessageLog messageLog = messageLogRepository.findById(messageLogId)
            .orElseThrow(() -> new IllegalArgumentException("Message log not found: " + messageLogId));
        
        String messageId = messageLog.getMessageId();
        
        // Atomically claim the message for retry using EntityManager directly
        // This ensures the native query executes within the REQUIRES_NEW transaction
        Query updateQuery = entityManager.createNativeQuery(
            "UPDATE message_logs SET status = CAST('RETRYING' AS delivery_status), failure_type = NULL, updated_at = CURRENT_TIMESTAMP WHERE message_id = :messageId AND status = CAST('FAILED' AS delivery_status)"
        );
        updateQuery.setParameter("messageId", messageId);
        int rowsAffected = updateQuery.executeUpdate();
        
        // Clear persistence context after native update to ensure fresh entity state
        entityManager.clear();
        
        if (rowsAffected == 0) {
            // Message was already claimed by another scheduler instance or not in FAILED status
            log.debug("Message {} was already claimed for retry or not in FAILED status, skipping", messageId);
            return;
        }
        
        // Reload to get updated status (after clearing persistence context)
        MessageLog updatedMessageLog = messageLogRepository.findByMessageId(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found after claim: " + messageId));
        
        String topic = getTopicForChannel(updatedMessageLog.getChannel());
        Integer currentRetryCount = updatedMessageLog.getRetryCount() != null ? updatedMessageLog.getRetryCount() : 0;
        NotificationChannel channel = updatedMessageLog.getChannel();
        
        // Check max retries again after claim (double-check)
        if (currentRetryCount >= MAX_RETRIES) {
            log.warn("Message {} exceeded max retries after claim, sending to DLQ", messageId);
            // Revert status back to FAILED for DLQ handling
            updatedMessageLog.setStatus(DeliveryStatus.FAILED);
            // ⚠️ EXPLICIT INVARIANT: Set failure_type when status is FAILED
            // If failure_type is null, set it to KAFKA (default for retry failures)
            if (updatedMessageLog.getFailureType() == null) {
                updatedMessageLog.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            }
            messageLogRepository.save(updatedMessageLog);
            // Will be handled by DLQ check in main loop
            return;
        }
        
        try {
            // Re-serialize the payload from the message log
            String payload = notificationService.serializeNotificationPayloadFromMessageLog(updatedMessageLog);
            
            // Record retry metric
            metricsService.recordMessageRetried(channel, currentRetryCount);
            
            // Register synchronization to send Kafka AFTER transaction commits
            // This ensures Kafka send happens outside the DB transaction boundary
            Timer.Sample kafkaPublishSample = Timer.start();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Kafka send happens AFTER successful DB commit
                    kafkaTemplate.send(topic, messageId, payload)
                        .whenComplete((result, ex) -> {
                            // Record Kafka publish latency
                            metricsService.recordKafkaPublishLatency(channel, kafkaPublishSample);
                            
                            if (ex == null) {
                                log.info("Successfully retried Kafka publish for message {} after commit", messageId);
                                // Update status and clear error on success (in new transaction via proxy)
                                self.updateRetrySuccess(messageId);
                            } else {
                                log.error("Kafka retry failed for message {} after commit", messageId, ex);
                                // Increment retry count only on failure (in new transaction via proxy)
                                self.updateRetryFailure(messageId, "Kafka retry failed: " + ex.getMessage());
                                
                                // Check if max retries exceeded and send to DLQ
                                self.checkAndSendToDlqIfNeeded(messageId);
                            }
                        });
                }
            });
            
            // Update status inside transaction (will commit before Kafka send)
            // Status already RETRYING from atomic claim, just clear error
            updatedMessageLog.setErrorMessage(null);
            messageLogRepository.save(updatedMessageLog);
            
            // Append to status history (claimed for retry)
            messageStatusHistoryService.appendStatusChange(
                messageId,
                DeliveryStatus.RETRYING,
                null,
                currentRetryCount
            );
            
        } catch (Exception e) {
            log.error("Error preparing retry for message {}", messageId, e);
            // On serialization error, increment retry count and mark as FAILED
            messageLog.setStatus(DeliveryStatus.FAILED);
            messageLog.setRetryCount(currentRetryCount + 1);
            messageLog.setErrorMessage("Payload serialization failed: " + e.getMessage());
            // Preserve existing failure type or default to KAFKA for retry failures
            if (messageLog.getFailureType() == null) {
                messageLog.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            }
            messageLogRepository.save(messageLog);
            throw new RuntimeException("Failed to prepare retry: " + e.getMessage(), e);
        }
    }
    
    /**
     * Update message on successful retry (sets to PENDING, clears error, keeps retry count unchanged).
     * After successful Kafka send, message goes to PENDING for worker to process.
     * Runs in a new transaction to avoid conflicts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRetrySuccess(String messageId) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            DeliveryStatus oldStatus = log.getStatus();
            // Change from RETRYING to PENDING (successfully requeued for processing)
            log.setStatus(DeliveryStatus.PENDING);
            log.setErrorMessage(null);
            // ⚠️ EXPLICIT INVARIANT: Clear failure_type when status is not FAILED
            // This makes intent explicit and keeps Java model aligned with DB constraint
            log.setFailureType(null);
            messageLogRepository.save(log);
            
            // Append to status history
            if (oldStatus != DeliveryStatus.PENDING) {
                messageStatusHistoryService.appendStatusChange(
                    messageId,
                    DeliveryStatus.PENDING,
                    null,
                    log.getRetryCount()
                );
            }
        });
    }
    
    /**
     * Update message on retry failure (sets to FAILED, increments retry count).
     * After failed Kafka send, message goes back to FAILED for next retry attempt.
     * Runs in a new transaction to avoid conflicts.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRetryFailure(String messageId, String errorMessage) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            DeliveryStatus oldStatus = log.getStatus();
            Integer currentRetryCount = log.getRetryCount() != null ? log.getRetryCount() : 0;
            // Change from RETRYING back to FAILED (retry attempt failed)
            log.setStatus(DeliveryStatus.FAILED);
            log.setRetryCount(currentRetryCount + 1); // Increment only on failure
            log.setErrorMessage(errorMessage);
            // Determine failure type based on error message
            // If error contains "Kafka", it's a KAFKA failure, otherwise preserve existing type
            if (errorMessage != null && errorMessage.toLowerCase().contains("kafka")) {
                log.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            } else if (log.getFailureType() == null) {
                // Preserve existing failure type if set, otherwise default to KAFKA for retry failures
                log.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            }
            messageLogRepository.save(log);
            
            // Append to status history
            if (oldStatus != DeliveryStatus.FAILED) {
                messageStatusHistoryService.appendStatusChange(
                    messageId,
                    DeliveryStatus.FAILED,
                    errorMessage,
                    log.getRetryCount()
                );
            }
        });
    }
    
    /**
     * Check if message exceeded max retries and send to DLQ if needed.
     * Runs in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndSendToDlqIfNeeded(String messageId) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            Integer currentRetryCount = log.getRetryCount() != null ? log.getRetryCount() : 0;
            if (currentRetryCount >= MAX_RETRIES) {
                self.sendToDeadLetterTopicInNewTransaction(log.getId());
            }
        });
    }
    
    /**
     * Send message to dead-letter topic after max retries exceeded.
     * This ensures failed messages are preserved for manual investigation.
     * Runs in a new transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendToDeadLetterTopicInNewTransaction(UUID messageLogId) {
        MessageLog messageLog = messageLogRepository.findById(messageLogId)
            .orElseThrow(() -> new IllegalArgumentException("Message log not found: " + messageLogId));
        
        String messageId = messageLog.getMessageId();
        String dlqTopic = getDeadLetterTopicForChannel(messageLog.getChannel());
        
        try {
            String payload = notificationService.serializeNotificationPayloadFromMessageLog(messageLog);
            kafkaTemplate.send(dlqTopic, messageId, payload);
            log.warn("Message {} sent to dead-letter topic {} after {} retries", 
                messageId, dlqTopic, messageLog.getRetryCount());
            
            // Record DLQ metric
            metricsService.recordDlq(messageLog.getChannel());
            
            // Update message status to indicate it's in DLQ
            messageLog.setErrorMessage("Max retries exceeded, sent to DLQ: " + 
                (messageLog.getErrorMessage() != null ? messageLog.getErrorMessage() : ""));
            messageLogRepository.save(messageLog);
        } catch (Exception e) {
            log.error("Failed to send message {} to dead-letter topic {}", messageId, dlqTopic, e);
            // Don't throw - we've already tried max retries, just log the error
        }
    }
    
    private String getDeadLetterTopicForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailDlqTopic;
            case WHATSAPP -> whatsappDlqTopic;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }
    
    private String getTopicForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailTopic;
            case WHATSAPP -> whatsappTopic;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }
}

