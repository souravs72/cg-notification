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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledMessageService {
    
    private static final String EMAIL_TOPIC = "notifications-email";
    private static final String WHATSAPP_TOPIC = "notifications-whatsapp";
    
    private final MessageLogRepository messageLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationResponse scheduleNotification(ScheduledNotificationRequest request, FrappeSite site) {
        String messageId = generateMessageId();
        
        try {
            // Create message log with SCHEDULED status
            MessageLog messageLog = createScheduledMessageLog(messageId, request, site);
            messageLogRepository.save(messageLog);

            log.info("Message {} scheduled for {}", messageId, request.getScheduledAt());
            return new NotificationResponse(messageId, "SCHEDULED", 
                "Notification scheduled for " + request.getScheduledAt());
            
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
                        "Failed to schedule: " + e.getMessage()
                    );
                }
            })
            .toList();
    }

    @Scheduled(fixedRate = 60000) // Check every minute
    @Transactional
    public void processScheduledMessages() {
        LocalDateTime now = LocalDateTime.now();
        List<MessageLog> scheduledMessages = messageLogRepository.findByStatusAndScheduledAtLessThanEqual(
            DeliveryStatus.SCHEDULED, now
        );

        log.info("Processing {} scheduled messages", scheduledMessages.size());

        for (MessageLog messageLog : scheduledMessages) {
            try {
                // Update status to PENDING
                messageLog.setStatus(DeliveryStatus.PENDING);
                messageLog.setScheduledAt(null); // Clear scheduled time
                messageLogRepository.save(messageLog);

                // Publish to Kafka
                String topic = getTopicForChannel(messageLog.getChannel());
                String payload = serializeNotificationPayload(messageLog);
                
                kafkaTemplate.send(topic, messageLog.getMessageId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish scheduled message {} to Kafka", 
                                messageLog.getMessageId(), ex);
                            messageLogRepository.findByMessageId(messageLog.getMessageId())
                                .ifPresent(ml -> {
                                    ml.setStatus(DeliveryStatus.FAILED);
                                    ml.setErrorMessage(ex.getMessage());
                                    messageLogRepository.save(ml);
                                });
                        } else {
                            log.info("Published scheduled message {} to Kafka", messageLog.getMessageId());
                        }
                    });

            } catch (Exception e) {
                log.error("Error processing scheduled message {}", messageLog.getMessageId(), e);
                messageLogRepository.findByMessageId(messageLog.getMessageId())
                    .ifPresent(ml -> {
                        ml.setStatus(DeliveryStatus.FAILED);
                        ml.setErrorMessage(e.getMessage());
                        messageLogRepository.save(ml);
                    });
            }
        }
    }

    private MessageLog createScheduledMessageLog(String messageId, ScheduledNotificationRequest request, FrappeSite site) {
        MessageLog messageLog = new MessageLog();
        messageLog.setMessageId(messageId);
        messageLog.setSiteId(site.getId());
        messageLog.setChannel(request.getChannel());
        messageLog.setStatus(DeliveryStatus.SCHEDULED);
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
        
        if (request.getMetadata() != null) {
            try {
                messageLog.setMetadata(objectMapper.writeValueAsString(request.getMetadata()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata", e);
            }
        }
        
        return messageLog;
    }


    private String serializeNotificationPayload(MessageLog messageLog) {
        try {
            com.clapgrow.notification.api.model.NotificationPayload payload = 
                new com.clapgrow.notification.api.model.NotificationPayload();
            payload.setMessageId(messageLog.getMessageId());
            payload.setSiteId(messageLog.getSiteId().toString());
            payload.setChannel(messageLog.getChannel().name());
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
            
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }

    private String getTopicForChannel(com.clapgrow.notification.api.enums.NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> EMAIL_TOPIC;
            case WHATSAPP -> WHATSAPP_TOPIC;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }

    private String generateMessageId() {
        return "MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }
}

