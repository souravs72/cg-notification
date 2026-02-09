package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
 * Service for retrying failed message publishes (SNS) and consumer failures.
 * Republishes to SNS; sends to SQS DLQ after max retries.
 *
 * ⚠️ RETRY COUNT OWNERSHIP: This service is the ONLY place that mutates retry_count.
 * Consumers (email-worker, whatsapp-worker) only set FAILED status and never increment retry_count.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingRetryService {

    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MINUTES = 5;
    private static final int RETRY_BATCH_SIZE = 50;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${messaging.sqs.queues.email-dlq:notifications-email-dlq}")
    private String emailDlqQueueName;

    @Value("${messaging.sqs.queues.whatsapp-dlq:notifications-whatsapp-dlq}")
    private String whatsappDlqQueueName;

    @Lazy
    @Autowired
    private MessagingRetryService self;

    private final MessageLogRepository messageLogRepository;
    private final SnsNotificationSender snsNotificationSender;
    private final SqsTemplate sqsTemplate;
    private final NotificationService notificationService;
    private final MessageStatusHistoryService messageStatusHistoryService;
    private final NotificationMetricsService metricsService;

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void retryFailedPublishes() {
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
        int totalProcessed = 0;
        while (true) {
            List<MessageLog> failedMessages = messageLogRepository.findFailedMessagesForRetry(
                DeliveryStatus.FAILED,
                com.clapgrow.notification.api.enums.FailureType.KAFKA,
                MAX_RETRIES,
                retryThreshold,
                org.springframework.data.domain.PageRequest.of(0, RETRY_BATCH_SIZE)
            );
            if (failedMessages.isEmpty()) {
                if (totalProcessed > 0) log.info("Completed publish retry batch. Total processed: {}", totalProcessed);
                break;
            }
            log.info("Found {} messages eligible for publish retry (batch)", failedMessages.size());
            for (MessageLog messageLog : failedMessages) {
                Integer currentRetryCount = messageLog.getRetryCount() != null ? messageLog.getRetryCount() : 0;
                if (currentRetryCount >= MAX_RETRIES) {
                    log.warn("Message {} has exceeded max retries ({}), sending to DLQ", messageLog.getMessageId(), currentRetryCount);
                    self.sendToDlqInNewTransaction(messageLog.getId());
                    continue;
                }
                try {
                    self.retrySingleMessageInNewTransaction(messageLog.getId());
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error retrying publish for message {}", messageLog.getMessageId(), e);
                }
            }
            if (failedMessages.size() < RETRY_BATCH_SIZE) break;
        }
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void retryFailedConsumerProcessing() {
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(RETRY_DELAY_MINUTES);
        int totalProcessed = 0;
        while (true) {
            List<MessageLog> failedMessages = messageLogRepository.findFailedMessagesForRetry(
                DeliveryStatus.FAILED,
                com.clapgrow.notification.api.enums.FailureType.CONSUMER,
                MAX_RETRIES,
                retryThreshold,
                org.springframework.data.domain.PageRequest.of(0, RETRY_BATCH_SIZE)
            );
            if (failedMessages.isEmpty()) {
                if (totalProcessed > 0) log.info("Completed consumer retry batch. Total processed: {}", totalProcessed);
                break;
            }
            log.info("Found {} messages eligible for consumer retry (batch)", failedMessages.size());
            for (MessageLog messageLog : failedMessages) {
                Integer currentRetryCount = messageLog.getRetryCount() != null ? messageLog.getRetryCount() : 0;
                if (currentRetryCount >= MAX_RETRIES) {
                    log.warn("Message {} has exceeded max retries ({}), sending to DLQ", messageLog.getMessageId(), currentRetryCount);
                    self.sendToDlqInNewTransaction(messageLog.getId());
                    continue;
                }
                try {
                    self.retrySingleConsumerMessageInNewTransaction(messageLog.getId());
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error retrying consumer processing for message {}", messageLog.getMessageId(), e);
                }
            }
            if (failedMessages.size() < RETRY_BATCH_SIZE) break;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void retrySingleConsumerMessageInNewTransaction(UUID messageLogId) {
        retrySingleMessageInNewTransaction(messageLogId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 30)
    public void retrySingleMessageInNewTransaction(UUID messageLogId) {
        MessageLog messageLog = messageLogRepository.findById(messageLogId)
            .orElseThrow(() -> new IllegalArgumentException("Message log not found: " + messageLogId));
        String messageId = messageLog.getMessageId();

        Query updateQuery = entityManager.createNativeQuery(
            "UPDATE message_logs SET status = CAST('RETRYING' AS delivery_status), failure_type = NULL, updated_at = CURRENT_TIMESTAMP WHERE message_id = :messageId AND status = CAST('FAILED' AS delivery_status)"
        );
        updateQuery.setParameter("messageId", messageId);
        int rowsAffected = updateQuery.executeUpdate();
        entityManager.clear();

        if (rowsAffected == 0) {
            log.debug("Message {} was already claimed for retry or not in FAILED status, skipping", messageId);
            return;
        }

        MessageLog updatedMessageLog = messageLogRepository.findByMessageId(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found after claim: " + messageId));
        Integer currentRetryCount = updatedMessageLog.getRetryCount() != null ? updatedMessageLog.getRetryCount() : 0;
        NotificationChannel channel = updatedMessageLog.getChannel();

        if (currentRetryCount >= MAX_RETRIES) {
            log.warn("Message {} exceeded max retries after claim, sending to DLQ", messageId);
            updatedMessageLog.setStatus(DeliveryStatus.FAILED);
            if (updatedMessageLog.getFailureType() == null) {
                updatedMessageLog.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            }
            messageLogRepository.save(updatedMessageLog);
            return;
        }

        try {
            String payload = notificationService.serializeNotificationPayloadFromMessageLog(updatedMessageLog);
            metricsService.recordMessageRetried(channel, currentRetryCount);

            Timer.Sample publishSample = Timer.start();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        snsNotificationSender.publish(channel, messageId, payload);
                        metricsService.recordMessagingPublishLatency(channel, publishSample);
                        log.info("Successfully retried publish for message {} after commit", messageId);
                        self.updateRetrySuccess(messageId);
                    } catch (Exception ex) {
                        log.error("Retry publish failed for message {} after commit", messageId, ex);
                        metricsService.recordMessagingPublishLatency(channel, publishSample);
                        self.updateRetryFailure(messageId, "SNS retry failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
                        self.checkAndSendToDlqIfNeeded(messageId);
                    }
                }
            });

            updatedMessageLog.setErrorMessage(null);
            messageLogRepository.save(updatedMessageLog);
            messageStatusHistoryService.appendStatusChange(messageId, DeliveryStatus.RETRYING, null, currentRetryCount);
        } catch (Exception e) {
            log.error("Error preparing retry for message {}", messageId, e);
            messageLog.setStatus(DeliveryStatus.FAILED);
            messageLog.setRetryCount(currentRetryCount + 1);
            messageLog.setErrorMessage("Payload serialization failed: " + e.getMessage());
            if (messageLog.getFailureType() == null) {
                messageLog.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            }
            messageLogRepository.save(messageLog);
            throw new RuntimeException("Failed to prepare retry: " + e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRetrySuccess(String messageId) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            DeliveryStatus oldStatus = log.getStatus();
            log.setStatus(DeliveryStatus.PENDING);
            log.setErrorMessage(null);
            log.setFailureType(null);
            messageLogRepository.save(log);
            if (oldStatus != DeliveryStatus.PENDING) {
                messageStatusHistoryService.appendStatusChange(messageId, DeliveryStatus.PENDING, null, log.getRetryCount());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateRetryFailure(String messageId, String errorMessage) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            DeliveryStatus oldStatus = log.getStatus();
            Integer currentRetryCount = log.getRetryCount() != null ? log.getRetryCount() : 0;
            log.setStatus(DeliveryStatus.FAILED);
            log.setRetryCount(currentRetryCount + 1);
            log.setErrorMessage(errorMessage);
            if (errorMessage != null && (errorMessage.toLowerCase().contains("sns") || errorMessage.toLowerCase().contains("kafka"))) {
                log.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            } else if (log.getFailureType() == null) {
                log.setFailureType(com.clapgrow.notification.api.enums.FailureType.KAFKA);
            }
            messageLogRepository.save(log);
            if (oldStatus != DeliveryStatus.FAILED) {
                messageStatusHistoryService.appendStatusChange(messageId, DeliveryStatus.FAILED, errorMessage, log.getRetryCount());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndSendToDlqIfNeeded(String messageId) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            Integer currentRetryCount = log.getRetryCount() != null ? log.getRetryCount() : 0;
            if (currentRetryCount >= MAX_RETRIES) {
                self.sendToDlqInNewTransaction(log.getId());
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendToDlqInNewTransaction(UUID messageLogId) {
        MessageLog messageLog = messageLogRepository.findById(messageLogId)
            .orElseThrow(() -> new IllegalArgumentException("Message log not found: " + messageLogId));
        String messageId = messageLog.getMessageId();
        String dlqQueueName = getDlqQueueForChannel(messageLog.getChannel());
        try {
            String payload = notificationService.serializeNotificationPayloadFromMessageLog(messageLog);
            sqsTemplate.send(to -> to.queue(dlqQueueName).payload(payload));
            log.warn("Message {} sent to DLQ {} after {} retries", messageId, dlqQueueName, messageLog.getRetryCount());
            metricsService.recordDlq(messageLog.getChannel());
            messageLog.setErrorMessage("Max retries exceeded, sent to DLQ: " +
                (messageLog.getErrorMessage() != null ? messageLog.getErrorMessage() : ""));
            messageLogRepository.save(messageLog);
        } catch (Exception e) {
            log.error("Failed to send message {} to DLQ {}", messageId, dlqQueueName, e);
        }
    }

    private String getDlqQueueForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailDlqQueueName;
            case WHATSAPP -> whatsappDlqQueueName;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }
}
