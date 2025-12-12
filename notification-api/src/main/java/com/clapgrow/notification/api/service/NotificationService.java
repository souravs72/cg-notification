package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.dto.BulkNotificationRequest;
import com.clapgrow.notification.api.dto.NotificationRequest;
import com.clapgrow.notification.api.dto.NotificationResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private static final String EMAIL_TOPIC = "notifications-email";
    private static final String WHATSAPP_TOPIC = "notifications-whatsapp";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MessageLogRepository messageLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request, FrappeSite site) {
        String messageId = generateMessageId();
        
        try {
            // Create message log
            MessageLog messageLog = createMessageLog(messageId, request, site);
            messageLogRepository.save(messageLog);

            // Publish to Kafka
            String topic = getTopicForChannel(request.getChannel());
            String payload = serializeNotificationPayload(messageId, request, site.getId());
            
            kafkaTemplate.send(topic, messageId, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish message {} to Kafka topic {}", messageId, topic, ex);
                        updateMessageLogStatus(messageId, DeliveryStatus.FAILED, ex.getMessage());
                    } else {
                        log.info("Published message {} to Kafka topic {}", messageId, topic);
                    }
                });

            return new NotificationResponse(messageId, "ACCEPTED", "Notification queued successfully");
            
        } catch (Exception e) {
            log.error("Error processing notification request", e);
            throw new RuntimeException("Failed to process notification: " + e.getMessage(), e);
        }
    }

    @Transactional
    public List<NotificationResponse> sendBulkNotifications(BulkNotificationRequest request, FrappeSite site) {
        List<NotificationResponse> responses = new ArrayList<>();
        
        for (NotificationRequest notificationRequest : request.getNotifications()) {
            try {
                NotificationResponse response = sendNotification(notificationRequest, site);
                responses.add(response);
            } catch (Exception e) {
                log.error("Error processing bulk notification", e);
                responses.add(new NotificationResponse(
                    generateMessageId(),
                    "FAILED",
                    "Failed to process notification: " + e.getMessage()
                ));
            }
        }
        
        return responses;
    }

    private MessageLog createMessageLog(String messageId, NotificationRequest request, FrappeSite site) {
        MessageLog messageLog = new MessageLog();
        messageLog.setMessageId(messageId);
        messageLog.setSiteId(site.getId());
        messageLog.setChannel(request.getChannel());
        messageLog.setStatus(DeliveryStatus.PENDING);
        messageLog.setRecipient(request.getRecipient());
        messageLog.setSubject(request.getSubject());
        messageLog.setBody(request.getBody());
        messageLog.setRetryCount(0);
        messageLog.setCreatedBy(site.getSiteName());
        
        // WhatsApp specific fields
        messageLog.setImageUrl(request.getImageUrl());
        messageLog.setVideoUrl(request.getVideoUrl());
        messageLog.setDocumentUrl(request.getDocumentUrl());
        messageLog.setFileName(request.getFileName());
        messageLog.setCaption(request.getCaption());
        
        // Email specific fields
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

    private String serializeNotificationPayload(String messageId, NotificationRequest request, UUID siteId) {
        try {
            com.clapgrow.notification.api.model.NotificationPayload payload = 
                new com.clapgrow.notification.api.model.NotificationPayload();
            payload.setMessageId(messageId);
            payload.setSiteId(siteId.toString());
            payload.setChannel(request.getChannel().name());
            payload.setRecipient(request.getRecipient());
            payload.setSubject(request.getSubject());
            payload.setBody(request.getBody());
            payload.setImageUrl(request.getImageUrl());
            payload.setVideoUrl(request.getVideoUrl());
            payload.setDocumentUrl(request.getDocumentUrl());
            payload.setFileName(request.getFileName());
            payload.setCaption(request.getCaption());
            payload.setFromEmail(request.getFromEmail());
            payload.setFromName(request.getFromName());
            payload.setIsHtml(request.getIsHtml());
            payload.setMetadata(request.getMetadata());
            
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }

    private String getTopicForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> EMAIL_TOPIC;
            case WHATSAPP -> WHATSAPP_TOPIC;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }

    private String generateMessageId() {
        return "MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private void updateMessageLogStatus(String messageId, DeliveryStatus status, String errorMessage) {
        messageLogRepository.findByMessageId(messageId).ifPresent(log -> {
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            log.setUpdatedAt(LocalDateTime.now());
            messageLogRepository.save(log);
        });
    }

}

