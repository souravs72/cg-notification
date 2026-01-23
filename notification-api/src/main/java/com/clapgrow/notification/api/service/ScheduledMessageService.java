package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.dto.NotificationResponse;
import com.clapgrow.notification.api.dto.ScheduledNotificationRequest;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.repository.FrappeSiteRepository;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledMessageService {
    
    @Value("${spring.kafka.topics.email:notifications-email}")
    private String emailTopic;
    
    @Value("${spring.kafka.topics.whatsapp:notifications-whatsapp}")
    private String whatsappTopic;
    
    private final MessageLogRepository messageLogRepository;
    private final FrappeSiteRepository siteRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unused") // Reserved for future use
    private final WasenderConfigService wasenderConfigService;
    @SuppressWarnings("unused") // Reserved for future use
    private final SendGridConfigService sendGridConfigService;
    @SuppressWarnings("unused") // Reserved for future use
    private final com.clapgrow.notification.api.repository.WhatsAppSessionRepository whatsAppSessionRepository;
    @SuppressWarnings("unused") // Reserved for future use
    private final WasenderQRServiceClient wasenderQRServiceClient;
    private final MessageStatusHistoryService messageStatusHistoryService;

    @Transactional
    public NotificationResponse scheduleNotification(ScheduledNotificationRequest request, FrappeSite site) {
        // Validate scheduled time is in the future
        if (request.getScheduledAt() == null || !request.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled time must be in the future");
        }
        
        String messageId = generateMessageId();
        
        try {
            // Create message log with SCHEDULED status
            MessageLog messageLog = createScheduledMessageLog(messageId, request, site);
            messageLogRepository.save(messageLog);

            log.info("Message {} scheduled for {}", messageId, request.getScheduledAt());
            return new NotificationResponse(messageId, "SCHEDULED", 
                "Notification scheduled for " + request.getScheduledAt(),
                request.getChannel().name());
            
        } catch (Exception e) {
            log.error("Error scheduling notification", e);
            throw new RuntimeException("Failed to schedule notification: " + e.getMessage(), e);
        }
    }

    @Transactional
    public List<NotificationResponse> scheduleBulkNotifications(
            List<ScheduledNotificationRequest> requests, FrappeSite site) {
        return requests.stream()
            .map(request -> {
                try {
                    return scheduleNotification(request, site);
                } catch (Exception e) {
                    log.error("Error scheduling bulk notification", e);
                    return new NotificationResponse(
                        generateMessageId(),
                        "FAILED",
                        "Failed to schedule: " + e.getMessage(),
                        request.getChannel() != null ? request.getChannel().name() : null
                    );
                }
            })
            .toList();
    }

    private static final int SCHEDULED_BATCH_SIZE = 100; // Process max 100 messages per batch for backpressure
    
    /**
     * Process scheduled messages.
     * Each message is processed in its own transaction to prevent rollback cascading.
     * This ensures that if one message fails, others are not affected.
     * 
     * ⚠️ BACKPRESSURE: Processes messages in batches of SCHEDULED_BATCH_SIZE to prevent
     * hot-loop when backlog grows. Loops until no more messages found.
     */
    @Scheduled(fixedRate = 60000) // Check every minute
    public void processScheduledMessages() {
        LocalDateTime now = LocalDateTime.now();
        int totalProcessed = 0;
        
        // Process in batches until no more messages found (backpressure)
        while (true) {
            // Fetch only message IDs to avoid loading full entities in the outer transaction
            List<String> messageIds = messageLogRepository.findMessageIdsByStatusAndScheduledAtLessThanEqual(
                DeliveryStatus.SCHEDULED.name(), 
                now,
                org.springframework.data.domain.PageRequest.of(0, SCHEDULED_BATCH_SIZE)
            );

            if (messageIds.isEmpty()) {
                if (totalProcessed > 0) {
                    log.info("Completed scheduled message batch processing. Total processed: {}", totalProcessed);
                } else {
                    log.debug("No scheduled messages ready for processing");
                }
                break;
            }

            log.info("Processing {} scheduled messages (batch)", messageIds.size());

            for (String messageId : messageIds) {
                try {
                    processSingleScheduledMessage(messageId);
                    totalProcessed++;
                } catch (Exception e) {
                    log.error("Error processing scheduled message {}", messageId, e);
                    // Update status in a separate transaction
                    updateMessageLogStatus(messageId, DeliveryStatus.FAILED, e.getMessage(),
                                         com.clapgrow.notification.api.enums.FailureType.CONSUMER);
                }
            }
            
            // If we got fewer messages than batch size, we're done
            if (messageIds.size() < SCHEDULED_BATCH_SIZE) {
                if (totalProcessed > 0) {
                    log.info("Completed scheduled message batch processing. Total processed: {}", totalProcessed);
                }
                break;
            }
        }
    }
    
    /**
     * Process a single scheduled message in its own transaction.
     * Uses atomic update to prevent duplicate processing when multiple scheduler instances run concurrently.
     * This ensures isolation - if one message fails, others are not affected.
     */
    @Transactional
    public void processSingleScheduledMessage(String messageId) {
        // Atomically update status from SCHEDULED to PENDING
        // This prevents duplicate processing if multiple scheduler instances run concurrently
        int rowsAffected = messageLogRepository.atomicallyUpdateScheduledToPending(messageId);
        
        if (rowsAffected == 0) {
            // Message was already processed or not found
            log.warn("Message {} was not in SCHEDULED status or not found. Skipping.", messageId);
            return;
        }
        
        // Fetch the updated message log
        MessageLog messageLog = messageLogRepository.findByMessageId(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found after atomic update: " + messageId));

        // Prepare Kafka payload BEFORE transaction commits
        String topic = getTopicForChannel(messageLog.getChannel());
        String payload = serializeNotificationPayload(messageLog);
        
        // Register synchronization to send Kafka AFTER transaction commits
        // This ensures Kafka send happens outside the DB transaction boundary
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Kafka send happens AFTER successful DB commit
                kafkaTemplate.send(topic, messageId, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish scheduled message {} to Kafka topic {} after commit", 
                                messageId, topic, ex);
                            // Update message status to FAILED for reconciliation
                            // This runs in a separate transaction to avoid conflicts
                            try {
                                updateMessageLogStatus(messageId, DeliveryStatus.FAILED, 
                                    "Kafka publish failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                                    com.clapgrow.notification.api.enums.FailureType.KAFKA);
                            } catch (Exception updateEx) {
                                log.error("Failed to update message log status after Kafka failure for messageId={}", messageId, updateEx);
                            }
                        } else {
                            log.info("Published scheduled message {} to Kafka topic {} after commit", messageId, topic);
                        }
                    });
            }
        });
    }
    
    private final StatusTransitionValidator statusTransitionValidator;
    
    /**
     * Update message log status.
     * Uses REQUIRES_NEW propagation to ensure it runs in a separate transaction,
     * which is safe to call from Kafka callbacks or async operations.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private void updateMessageLogStatus(String messageId, DeliveryStatus status, String errorMessage,
                                       com.clapgrow.notification.api.enums.FailureType failureType) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            DeliveryStatus oldStatus = log.getStatus();
            
            // Validate status transition
            if (oldStatus != status) {
                statusTransitionValidator.assertValidTransition(oldStatus, status);
            }
            
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            // Set failure_type only when status is FAILED, clear it otherwise
            if (status == DeliveryStatus.FAILED) {
                log.setFailureType(failureType);
            } else {
                log.setFailureType(null);
            }
            log.setUpdatedAt(LocalDateTime.now());
            messageLogRepository.save(log);
            
            // Append to status history if status changed
            if (oldStatus != status) {
                messageStatusHistoryService.appendStatusChange(
                    messageId, 
                    status, 
                    errorMessage, 
                    log.getRetryCount()
                );
            }
        });
    }

    private MessageLog createScheduledMessageLog(String messageId, ScheduledNotificationRequest request, FrappeSite site) {
        MessageLog messageLog = new MessageLog();
        messageLog.setMessageId(messageId);
        messageLog.setSiteId(site.getId());
        messageLog.setChannel(request.getChannel());
        messageLog.setStatus(DeliveryStatus.SCHEDULED);
        // ⚠️ EXPLICIT INVARIANT: Set failure_type to null for non-FAILED status
        // This makes intent explicit and keeps Java model aligned with DB constraint
        messageLog.setFailureType(null);
        messageLog.setRecipient(request.getRecipient());
        messageLog.setSubject(request.getSubject());
        messageLog.setBody(request.getBody());
        messageLog.setScheduledAt(request.getScheduledAt());
        messageLog.setRetryCount(0);
        messageLog.setCreatedBy(site.getSiteName());
        
        // WhatsApp fields
        messageLog.setImageUrl(request.getImageUrl());
        messageLog.setVideoUrl(request.getVideoUrl());
        messageLog.setDocumentUrl(request.getDocumentUrl());
        messageLog.setFileName(request.getFileName());
        messageLog.setCaption(request.getCaption());
        
        // Email fields
        messageLog.setFromEmail(request.getFromEmail());
        messageLog.setFromName(request.getFromName());
        messageLog.setIsHtml(request.getIsHtml() != null ? request.getIsHtml() : false);
        
        // Store metadata including WASender API key if provided
        // ⚠️ SECURITY: API key is stored in metadata but should NEVER be logged or exposed
        Map<String, String> metadata = request.getMetadata() != null 
            ? new java.util.HashMap<>(request.getMetadata()) 
            : new java.util.HashMap<>();
        
        // ⚠️ SECURITY: API keys are NOT stored in metadata - resolved in consumer from database
        // Consumer will resolve API key using whatsappSessionName or siteId from payload
        
        if (!metadata.isEmpty()) {
            try {
                messageLog.setMetadata(objectMapper.writeValueAsString(metadata));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata", e);
            }
        }
        
        return messageLog;
    }


    private String serializeNotificationPayload(MessageLog messageLog) {
        try {
            // Get site information to include WhatsApp session and API key
            FrappeSite site = siteRepository.findById(messageLog.getSiteId())
                .orElseThrow(() -> new IllegalArgumentException("Site not found: " + messageLog.getSiteId()));
            
            com.clapgrow.notification.api.model.NotificationPayload payload = 
                new com.clapgrow.notification.api.model.NotificationPayload();
            payload.setMessageId(messageLog.getMessageId());
            payload.setSiteId(messageLog.getSiteId());  // UUID, not String - API key resolved in consumer
            payload.setChannel(messageLog.getChannel());  // Enum, not String
            payload.setRecipient(messageLog.getRecipient());
            payload.setSubject(messageLog.getSubject());
            payload.setBody(messageLog.getBody());
            payload.setImageUrl(messageLog.getImageUrl());
            payload.setVideoUrl(messageLog.getVideoUrl());
            payload.setDocumentUrl(messageLog.getDocumentUrl());
            payload.setFileName(messageLog.getFileName());
            payload.setCaption(messageLog.getCaption());
            payload.setFromEmail(messageLog.getFromEmail());
            payload.setFromName(messageLog.getFromName());
            payload.setIsHtml(messageLog.getIsHtml());
            payload.setWhatsappSessionName(site.getWhatsappSessionName());
            
            // ⚠️ SECURITY: API keys are NOT included in payload - resolved in consumer from database
            // Consumer will resolve API key using whatsappSessionName or siteId from payload
            if (messageLog.getChannel() == com.clapgrow.notification.api.enums.NotificationChannel.WHATSAPP) {
                // Clean metadata (remove any API keys that might have been stored previously)
                Map<String, String> metadataMap = null;
                if (messageLog.getMetadata() != null) {
                    try {
                        metadataMap = objectMapper.readValue(
                            messageLog.getMetadata(),
                            new TypeReference<Map<String, String>>() {}
                        );
                        // Remove wasenderApiKey if present (legacy cleanup)
                        if (metadataMap != null) {
                            metadataMap.remove("wasenderApiKey");
                        }
                    } catch (Exception e) {
                        // Ignore - metadata parsing failed
                    }
                }
                
                // Set metadata without API keys
                if (metadataMap != null && !metadataMap.isEmpty()) {
                    payload.setMetadata(metadataMap);
                }
            } else {
                // For non-WhatsApp channels, set metadata as-is
                if (messageLog.getMetadata() != null) {
                    try {
                        payload.setMetadata(objectMapper.readValue(
                            messageLog.getMetadata(),
                            new TypeReference<Map<String, String>>() {}
                        ));
                    } catch (Exception e) {
                        log.warn("Failed to parse metadata for scheduled message", e);
                    }
                }
            }
            
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }

    private String getTopicForChannel(com.clapgrow.notification.api.enums.NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailTopic;
            case WHATSAPP -> whatsappTopic;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }

    private String generateMessageId() {
        return "MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }
}

