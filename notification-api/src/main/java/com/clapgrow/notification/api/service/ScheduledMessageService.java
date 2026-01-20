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
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledMessageService {
    
    private static final String EMAIL_TOPIC = "notifications-email";
    private static final String WHATSAPP_TOPIC = "notifications-whatsapp";
    
    private final MessageLogRepository messageLogRepository;
    private final FrappeSiteRepository siteRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final WasenderConfigService wasenderConfigService;
    private final SendGridConfigService sendGridConfigService;
    private final com.clapgrow.notification.api.repository.WhatsAppSessionRepository whatsAppSessionRepository;
    private final WasenderQRService wasenderQRService;

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

    @Scheduled(fixedRate = 60000) // Check every minute
    @Transactional
    public void processScheduledMessages() {
        LocalDateTime now = LocalDateTime.now();
        List<MessageLog> scheduledMessages = messageLogRepository.findByStatusAndScheduledAtLessThanEqual(
            DeliveryStatus.SCHEDULED.name(), now
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
            // Get site information to include WhatsApp session and API key
            FrappeSite site = siteRepository.findById(messageLog.getSiteId())
                .orElseThrow(() -> new IllegalArgumentException("Site not found: " + messageLog.getSiteId()));
            
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
            payload.setWhatsappSessionName(site.getWhatsappSessionName());
            // Use global SendGrid API key
            sendGridConfigService.getApiKey().ifPresent(payload::setSendgridApiKey);
            
            // Include WASender API key for WhatsApp messages
            if (messageLog.getChannel() == com.clapgrow.notification.api.enums.NotificationChannel.WHATSAPP) {
                String sessionApiKey = null;
                String requestedSessionName = site.getWhatsappSessionName();
                
                // If a session is specified, try to get session-specific API key
                if (requestedSessionName != null && !requestedSessionName.trim().isEmpty()) {
                    try {
                        // Try to find session by name in database
                        Optional<com.clapgrow.notification.api.entity.WhatsAppSession> sessionOpt = 
                            whatsAppSessionRepository.findFirstBySessionNameAndIsDeletedFalse(requestedSessionName.trim());
                        
                        if (sessionOpt.isPresent()) {
                            com.clapgrow.notification.api.entity.WhatsAppSession session = sessionOpt.get();
                            sessionApiKey = session.getSessionApiKey();
                            
                            if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                                log.info("Using session API key from database for scheduled message, session: {}", requestedSessionName);
                            } else {
                                log.warn("Session API key not found in database for session: {}, attempting to fetch from WASender", requestedSessionName);
                                
                                // Try to fetch from WASender API if we have a global API key
                                String globalApiKey = wasenderConfigService.getApiKey().orElse(null);
                                if (globalApiKey != null && session.getSessionId() != null) {
                                    try {
                                        Map<String, Object> sessionDetails = wasenderQRService.getSessionDetails(
                                            session.getSessionId(), globalApiKey);
                                        
                                        if (sessionDetails != null && Boolean.TRUE.equals(sessionDetails.get("success"))) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> data = (Map<String, Object>) sessionDetails.get("data");
                                            if (data != null) {
                                                // Look for API key in the response
                                                if (data.get("api_key") != null) {
                                                    sessionApiKey = data.get("api_key").toString();
                                                } else if (data.get("apiKey") != null) {
                                                    sessionApiKey = data.get("apiKey").toString();
                                                } else if (data.get("token") != null) {
                                                    sessionApiKey = data.get("token").toString();
                                                } else if (data.get("session_api_key") != null) {
                                                    sessionApiKey = data.get("session_api_key").toString();
                                                }
                                                
                                                if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                                                    // Update the session in database
                                                    session.setSessionApiKey(sessionApiKey.trim());
                                                    whatsAppSessionRepository.save(session);
                                                    log.info("Fetched and saved session API key for scheduled message, session: {}", requestedSessionName);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("Failed to fetch session API key from WASender for scheduled message, session: {}. Error: {}", 
                                                requestedSessionName, e.getMessage());
                                    }
                                }
                            }
                        } else {
                            log.warn("Session not found in database for scheduled message, session: {}", requestedSessionName);
                        }
                    } catch (Exception e) {
                        log.error("Error retrieving session API key for scheduled message, session: {}. Error: {}", 
                                requestedSessionName, e.getMessage(), e);
                    }
                }
                
                // Use session-specific API key if found, otherwise fallback to global API key
                if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                    payload.setWasenderApiKey(sessionApiKey.trim());
                    log.info("Using session-specific API key for scheduled WhatsApp message, session: {}", requestedSessionName);
                } else {
                    // Fallback to global config
                    wasenderConfigService.getApiKey().ifPresent(payload::setWasenderApiKey);
                    if (requestedSessionName != null && !requestedSessionName.trim().isEmpty()) {
                        log.warn("Session API key not found for scheduled message, session: {}. Using global API key as fallback.", requestedSessionName);
                    }
                }
                
                // Validate that we have an API key
                if (payload.getWasenderApiKey() == null || payload.getWasenderApiKey().trim().isEmpty()) {
                    throw new IllegalStateException("WASender API key is required for WhatsApp scheduled messages. Please configure it first or ensure the session API key is available.");
                }
            }
            
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

