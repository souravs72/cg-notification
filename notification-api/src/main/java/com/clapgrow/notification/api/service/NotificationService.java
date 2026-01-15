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
import java.util.Map;
import java.util.Optional;
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
    private final WasenderConfigService wasenderConfigService;
    private final SendGridConfigService sendGridConfigService;
    private final UserWasenderService userWasenderService;
    private final WasenderQRService wasenderQRService;
    private final com.clapgrow.notification.api.service.WhatsAppSessionService whatsAppSessionService;

    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request, FrappeSite site) {
        return sendNotification(request, site, null);
    }
    
    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request, FrappeSite site, jakarta.servlet.http.HttpSession session) {
        String messageId = generateMessageId();
        
        try {
            // Create message log
            MessageLog messageLog = createMessageLog(messageId, request, site);
            messageLogRepository.save(messageLog);

            // Publish to Kafka
            String topic = getTopicForChannel(request.getChannel());
            String payload = serializeNotificationPayload(messageId, request, site, session);
            
            kafkaTemplate.send(topic, messageId, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish message {} to Kafka topic {}", messageId, topic, ex);
                        updateMessageLogStatus(messageId, DeliveryStatus.FAILED, ex.getMessage());
                    } else {
                        log.info("Published message {} to Kafka topic {}", messageId, topic);
                    }
                });

            return new NotificationResponse(messageId, "ACCEPTED", "Notification queued successfully", request.getChannel().name());
            
        } catch (Exception e) {
            log.error("Error processing notification request", e);
            throw new RuntimeException("Failed to process notification: " + e.getMessage(), e);
        }
    }

    @Transactional
    public List<NotificationResponse> sendBulkNotifications(BulkNotificationRequest request, FrappeSite site) {
        return sendBulkNotifications(request, site, null);
    }
    
    @Transactional
    public List<NotificationResponse> sendBulkNotifications(BulkNotificationRequest request, FrappeSite site, jakarta.servlet.http.HttpSession session) {
        List<NotificationResponse> responses = new ArrayList<>();
        
        for (NotificationRequest notificationRequest : request.getNotifications()) {
            try {
                NotificationResponse response = sendNotification(notificationRequest, site, session);
                responses.add(response);
            } catch (Exception e) {
                log.error("Error processing bulk notification", e);
                responses.add(new NotificationResponse(
                    generateMessageId(),
                    "FAILED",
                    "Failed to process notification: " + e.getMessage(),
                    notificationRequest.getChannel() != null ? notificationRequest.getChannel().name() : null
                ));
            }
        }
        
        return responses;
    }

    private MessageLog createMessageLog(String messageId, NotificationRequest request, FrappeSite site) {
        MessageLog messageLog = new MessageLog();
        messageLog.setMessageId(messageId);
        if (site != null) {
            messageLog.setSiteId(site.getId());
            messageLog.setCreatedBy(site.getSiteName());
        } else {
            messageLog.setCreatedBy("Dashboard User");
        }
        messageLog.setChannel(request.getChannel());
        messageLog.setStatus(DeliveryStatus.PENDING);
        messageLog.setRecipient(request.getRecipient());
        messageLog.setSubject(request.getSubject());
        messageLog.setBody(request.getBody());
        messageLog.setRetryCount(0);
        
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

    private String serializeNotificationPayload(String messageId, NotificationRequest request, FrappeSite site, jakarta.servlet.http.HttpSession session) {
        try {
            com.clapgrow.notification.api.model.NotificationPayload payload = 
                new com.clapgrow.notification.api.model.NotificationPayload();
            payload.setMessageId(messageId);
            if (site != null) {
                payload.setSiteId(site.getId().toString());
                payload.setWhatsappSessionName(site.getWhatsappSessionName());
                // Use global SendGrid API key
                sendGridConfigService.getApiKey().ifPresent(payload::setSendgridApiKey);
                // Use request email, then site email, then global SendGrid config defaults
                payload.setFromEmail(request.getFromEmail() != null ? request.getFromEmail() 
                    : (site.getEmailFromAddress() != null ? site.getEmailFromAddress() 
                        : sendGridConfigService.getEmailFromAddress().orElse(null)));
                payload.setFromName(request.getFromName() != null ? request.getFromName() 
                    : (site.getEmailFromName() != null ? site.getEmailFromName() 
                        : sendGridConfigService.getEmailFromName().orElse(null)));
            } else {
                // No site - use defaults and get user's session info
                payload.setSiteId(null);
                payload.setFromEmail(request.getFromEmail());
                payload.setFromName(request.getFromName());
                
                // For WhatsApp, get user's first connected session
                if (request.getChannel() == NotificationChannel.WHATSAPP && session != null) {
                    String whatsappSessionName = getUserDefaultWhatsAppSession(session);
                    payload.setWhatsappSessionName(whatsappSessionName);
                }
            }
            
            payload.setChannel(request.getChannel().name());
            payload.setRecipient(request.getRecipient());
            payload.setSubject(request.getSubject());
            payload.setBody(request.getBody());
            payload.setImageUrl(request.getImageUrl());
            payload.setVideoUrl(request.getVideoUrl());
            payload.setDocumentUrl(request.getDocumentUrl());
            payload.setFileName(request.getFileName());
            payload.setCaption(request.getCaption());
            
            // Include WASender API key for WhatsApp messages
            if (request.getChannel() == NotificationChannel.WHATSAPP) {
                // Check if a specific session is requested (from request or site)
                String requestedSessionName = request.getWhatsappSessionName();
                if ((requestedSessionName == null || requestedSessionName.trim().isEmpty()) && site != null && site.getWhatsappSessionName() != null) {
                    // Use site's session name if request doesn't specify one
                    requestedSessionName = site.getWhatsappSessionName();
                }
                
                // If a session is specified (from request or site), we MUST use that session's API key
                if (requestedSessionName != null && !requestedSessionName.trim().isEmpty()) {
                    String sessionApiKey = null;
                    boolean sessionApiKeyFound = false;
                    
                    // Try to get session-specific API key from database first
                    if (session != null) {
                        Optional<String> sessionApiKeyOpt = whatsAppSessionService.getSessionApiKey(requestedSessionName, session);
                        
                        if (sessionApiKeyOpt.isPresent()) {
                            sessionApiKey = sessionApiKeyOpt.get();
                            sessionApiKeyFound = true;
                            log.debug("Using session API key from database for session: {}", requestedSessionName);
                        } else {
                            // Session API key not found in database - try to fetch it from session details endpoint
                            log.info("Session API key not found in database for session: {}, attempting to fetch from session details endpoint", requestedSessionName);
                            try {
                                String userApiKey = userWasenderService.getApiKeyFromSession(session).orElse(null);
                                if (userApiKey != null) {
                                    // Try to get session by name to find session ID
                                    Optional<com.clapgrow.notification.api.entity.WhatsAppSession> sessionEntity = 
                                        whatsAppSessionService.getSessionByName(requestedSessionName, session);
                                    
                                    if (sessionEntity.isPresent()) {
                                        String sessionId = sessionEntity.get().getSessionId();
                                        if (sessionId != null && !sessionId.trim().isEmpty()) {
                                            // Fetch session details to get API key
                                            Map<String, Object> sessionDetails = wasenderQRService.getSessionDetails(sessionId, userApiKey);
                                            
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
                                                    }
                                                    
                                                    if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                                                        try {
                                                            // Update the session in database with the fetched API key
                                                            whatsAppSessionService.updateSessionApiKey(sessionId, sessionApiKey, session);
                                                            sessionApiKeyFound = true;
                                                            log.info("Successfully fetched and saved session API key for session: {} (ID: {})", 
                                                                    requestedSessionName, sessionId);
                                                        } catch (Exception updateEx) {
                                                            log.error("Failed to update session API key in database for session: {} (ID: {}). Error: {}", 
                                                                    requestedSessionName, sessionId, updateEx.getMessage(), updateEx);
                                                            // Still use the API key for this message even if DB update failed
                                                            sessionApiKeyFound = true;
                                                        }
                                                    } else {
                                                        log.warn("Session API key not found in response data. Available fields: {}", data.keySet());
                                                        if (log.isDebugEnabled()) {
                                                            log.debug("Full session details data: {}", data);
                                                        }
                                                    }
                                                } else {
                                                    log.warn("Session details response data is null for session: {}", sessionId);
                                                }
                                            } else {
                                                log.warn("Failed to fetch session details for session: {}. Response: {}", 
                                                        sessionId, sessionDetails != null ? sessionDetails.get("error") : "null");
                                            }
                                        } else {
                                            log.warn("Session ID is null or empty for session: {}", requestedSessionName);
                                        }
                                    } else {
                                        log.warn("Session entity not found in database for session name: {}", requestedSessionName);
                                    }
                                } else {
                                    log.warn("User API key not found in session, cannot fetch session details");
                                }
                            } catch (Exception e) {
                                log.error("Failed to fetch session API key from session details endpoint for session: {}. Error: {}", 
                                        requestedSessionName, e.getMessage(), e);
                            }
                        }
                    } else {
                        // Session is null - cannot retrieve session API key without user context
                        log.warn("HTTP session is null but session name '{}' was provided. Cannot retrieve session API key without user context.", requestedSessionName);
                    }
                    
                    // If session was explicitly chosen but API key not found, throw an error
                    if (!sessionApiKeyFound || sessionApiKey == null || sessionApiKey.trim().isEmpty()) {
                        String errorMessage;
                        if (session == null) {
                            errorMessage = String.format("Cannot use session '%s' without user authentication. Please authenticate first or use a site with a configured WhatsApp session.", requestedSessionName);
                        } else {
                            errorMessage = String.format("Session API key not found for session '%s'. Please ensure the session is created and connected, and the API key is available.", requestedSessionName);
                        }
                        throw new IllegalStateException(errorMessage);
                    }
                    
                    // Use the session-specific API key
                    payload.setWasenderApiKey(sessionApiKey.trim());
                    payload.setWhatsappSessionName(requestedSessionName.trim());
                    log.info("Using session-specific API key for session: {} to send message to: {}", requestedSessionName, request.getRecipient());
                    
                } else {
                    // No specific session chosen - fallback to user's API key or site's default
                    if (session != null) {
                        // Get from user session
                        userWasenderService.getApiKeyFromSession(session)
                            .ifPresent(payload::setWasenderApiKey);
                    } else if (site != null) {
                        // Fallback to global config
                        wasenderConfigService.getApiKey().ifPresent(payload::setWasenderApiKey);
                    }
                    
                    // Validate that we have an API key
                    if (payload.getWasenderApiKey() == null || payload.getWasenderApiKey().trim().isEmpty()) {
                        throw new IllegalStateException("WASender API key is required for WhatsApp messages. Please configure it first or specify a WhatsApp session.");
                    }
                }
            }
            
            if (request.getIsHtml() != null) {
                payload.setIsHtml(request.getIsHtml());
            } else {
                payload.setIsHtml(false);
            }
            payload.setMetadata(request.getMetadata());
            
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }
    
    /**
     * Get user's default WhatsApp session (first connected session)
     */
    private String getUserDefaultWhatsAppSession(jakarta.servlet.http.HttpSession session) {
        try {
            String apiKey = userWasenderService.getApiKeyFromSession(session).orElse(null);
            if (apiKey == null) {
                return null;
            }
            
            Map<String, Object> allSessions = wasenderQRService.getAllSessions(apiKey);
            if (allSessions != null && allSessions.containsKey("data")) {
                Object dataObj = allSessions.get("data");
                if (dataObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sessions = (List<Map<String, Object>>) dataObj;
                    // Find first connected session
                    for (Map<String, Object> sessionData : sessions) {
                        String status = sessionData.get("status") != null ? 
                            sessionData.get("status").toString() : "";
                        if ("connected".equalsIgnoreCase(status) || "CONNECTED".equalsIgnoreCase(status)) {
                            Object name = sessionData.get("name");
                            if (name != null) {
                                return name.toString();
                            }
                        }
                    }
                    // If no connected session, return first session's name
                    if (!sessions.isEmpty()) {
                        Object name = sessions.get(0).get("name");
                        if (name != null) {
                            return name.toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not get user's default WhatsApp session: {}", e.getMessage());
        }
        return null;
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

