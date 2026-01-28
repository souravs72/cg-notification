package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.dto.BulkNotificationRequest;
import com.clapgrow.notification.api.dto.NotificationRequest;
import com.clapgrow.notification.api.dto.NotificationResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import com.clapgrow.notification.api.repository.FrappeSiteRepository;
import com.clapgrow.notification.api.repository.MessageLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import io.micrometer.core.instrument.Timer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /** Self-injection so bulk loop calls go through the proxy and @Transactional applies. */
    @Lazy
    @Autowired
    private NotificationService self;

    @Value("${spring.kafka.topics.email:notifications-email}")
    private String emailTopic;
    
    @Value("${spring.kafka.topics.whatsapp:notifications-whatsapp}")
    private String whatsappTopic;
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MessageLogRepository messageLogRepository;
    private final FrappeSiteRepository siteRepository;
    private final ObjectMapper objectMapper;
    @SuppressWarnings("unused") // Reserved for future use
    private final WasenderConfigService wasenderConfigService;
    private final SendGridConfigService sendGridConfigService;
    @SuppressWarnings("unused") // No longer used after removing API key from payload - reserved for potential future use
    private final UserWasenderService userWasenderService;
    @SuppressWarnings("unused") // Reserved for future use
    private final com.clapgrow.notification.api.service.WhatsAppSessionService whatsAppSessionService;
    @SuppressWarnings("unused") // Reserved for future use
    private final com.clapgrow.notification.api.repository.WhatsAppSessionRepository sessionRepository;
    private final MessageStatusHistoryService messageStatusHistoryService;
    private final NotificationMetricsService metricsService;
    private final StatusTransitionValidator statusTransitionValidator;

    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request, FrappeSite site) {
        return sendNotification(request, site, null);
    }
    
    @Transactional
    public NotificationResponse sendNotification(NotificationRequest request, FrappeSite site, jakarta.servlet.http.HttpSession session) {
        String messageId = generateMessageId();
        
        // Create message log - DB transaction ONLY
        MessageLog messageLog = createMessageLog(messageId, request, site);
        messageLogRepository.save(messageLog);

        // Prepare Kafka payload BEFORE transaction commits
        String topic = getTopicForChannel(request.getChannel());
        String payload = serializeNotificationPayload(messageId, request, site, session);
        
        // ⚠️ METRIC SEMANTICS: This metric means "accepted by API", not "published to Kafka"
        // The message is accepted and persisted to DB, but Kafka publish happens asynchronously
        // after transaction commit. Kafka publish latency is tracked separately.
        metricsService.recordMessageSent(request.getChannel());
        
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
                        metricsService.recordKafkaPublishLatency(request.getChannel(), kafkaPublishSample);
                        
                        if (ex != null) {
                            log.error("Failed to publish message {} to Kafka topic {} after commit", messageId, topic, ex);
                            // Update message status to FAILED for reconciliation
                            // Metrics will be emitted automatically by MessageStatusHistoryService.appendStatusChange()
                            // This runs in a separate transaction to avoid conflicts
                            try {
                                // Update message status to FAILED for reconciliation
                                // KafkaRetryService will automatically retry these messages
                                // TODO: Implement dead-letter topic for permanent failures after max retries
                                // TODO: Add reconciliation job to check for orphaned messages
                                updateMessageLogStatus(messageId, DeliveryStatus.FAILED, 
                                    "Kafka publish failed: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()),
                                    com.clapgrow.notification.api.enums.FailureType.KAFKA);
                            } catch (Exception updateEx) {
                                log.error("Failed to update message log status after Kafka failure for messageId={}", messageId, updateEx);
                            }
                        } else {
                            log.info("Published message {} to Kafka topic {} after commit", messageId, topic);
                        }
                    });
            }
        });

        return new NotificationResponse(messageId, "ACCEPTED", "Notification queued successfully", request.getChannel().name());
    }

    /**
     * Send multiple notifications in bulk.
     * ⚠️ IMPORTANT: This method is NOT transactional.
     * Each sendNotification() call manages its own transaction.
     * This prevents rollback of all messages if one fails, and avoids
     * Kafka messages being sent for database transactions that never commit.
     */
    public List<NotificationResponse> sendBulkNotifications(BulkNotificationRequest request, FrappeSite site) {
        return sendBulkNotifications(request, site, null);
    }
    
    /**
     * Send multiple notifications in bulk.
     * ⚠️ IMPORTANT: This method is NOT transactional.
     * Each sendNotification() call manages its own transaction.
     * This prevents rollback of all messages if one fails, and avoids
     * Kafka messages being sent for database transactions that never commit.
     */
    public List<NotificationResponse> sendBulkNotifications(BulkNotificationRequest request, FrappeSite site, jakarta.servlet.http.HttpSession session) {
        List<NotificationResponse> responses = new ArrayList<>();
        
        for (NotificationRequest notificationRequest : request.getNotifications()) {
            try {
                NotificationResponse response = self.sendNotification(notificationRequest, site, session);
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
        // ⚠️ EXPLICIT INVARIANT: Set failure_type to null for non-FAILED status
        // This makes intent explicit and keeps Java model aligned with DB constraint
        messageLog.setFailureType(null);
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
                payload.setSiteId(site.getId());  // UUID, not String - API key resolved in consumer
                payload.setWhatsappSessionName(site.getWhatsappSessionName());
                // ⚠️ SECURITY: API keys NOT included in payload - resolved in consumer using siteId
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
            
            payload.setChannel(request.getChannel());  // Enum, not String
            payload.setRecipient(request.getRecipient());
            payload.setSubject(request.getSubject());
            payload.setBody(request.getBody());
            payload.setImageUrl(request.getImageUrl());
            payload.setVideoUrl(request.getVideoUrl());
            payload.setDocumentUrl(request.getDocumentUrl());
            payload.setFileName(request.getFileName());
            payload.setCaption(request.getCaption());
            
            // For WhatsApp, ensure whatsappSessionName is set with priority order:
            // 1. Request-specific session name (explicitly provided in request)
            // 2. Site's default session name (if site exists and has one configured)
            // 3. User's first connected session (if no site, resolved via getUserDefaultWhatsAppSession)
            // This provides flexibility: explicit override > site default > user default
            if (request.getChannel() == NotificationChannel.WHATSAPP) {
                String requestedSessionName = request.getWhatsappSessionName();
                if ((requestedSessionName == null || requestedSessionName.trim().isEmpty()) 
                    && site != null && site.getWhatsappSessionName() != null) {
                    // Priority 2: Use site's session name if request doesn't specify one
                    payload.setWhatsappSessionName(site.getWhatsappSessionName());
                } else if (requestedSessionName != null && !requestedSessionName.trim().isEmpty()) {
                    // Priority 1: Use explicitly requested session name
                    payload.setWhatsappSessionName(requestedSessionName.trim());
                }
                // Priority 3: User's first connected session is set earlier (line 198)
                
                // ⚠️ SECURITY: API keys are NOT included in payload - resolved in consumer using whatsappSessionName or siteId
                // Consumer will resolve API key from database: whatsappSessionName -> WhatsAppSession -> sessionApiKey
                // or siteId -> FrappeSite -> whatsappSessionName -> WhatsAppSession -> sessionApiKey
            }
            
            // Set isHtml (default false if null)
            payload.setIsHtml(request.getIsHtml() != null ? request.getIsHtml() : false);
            payload.setMetadata(request.getMetadata());
            
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification payload", e);
        }
    }
    
    /**
     * Get user's default WhatsApp session (first connected session from database).
     * 
     * ⚠️ HOT PATH: This is called during message sending. Uses database lookup
     * instead of provider API to avoid blocking threads. Database is the source
     * of truth for user sessions (synced from provider via admin operations).
     */
    private String getUserDefaultWhatsAppSession(jakarta.servlet.http.HttpSession session) {
        try {
            if (session == null) {
                return null;
            }
            String userIdStr = (String) session.getAttribute("userId");
            if (userIdStr == null) {
                return null;
            }
            UUID userId = UUID.fromString(userIdStr);
            
            // Use database lookup - fast, non-blocking, no external API calls
            // Database is synced from provider via admin operations
            List<com.clapgrow.notification.api.entity.WhatsAppSession> connectedSessions = 
                sessionRepository.findByUserIdAndIsDeletedFalseAndStatusOrderByCreatedAtDesc(userId, "CONNECTED");
            
            if (!connectedSessions.isEmpty()) {
                return connectedSessions.get(0).getSessionName();
            }
            
            // Fallback: return first session (even if not connected)
            List<com.clapgrow.notification.api.entity.WhatsAppSession> allSessions = 
                sessionRepository.findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(userId);
            if (!allSessions.isEmpty()) {
                return allSessions.get(0).getSessionName();
            }
        } catch (Exception e) {
            log.warn("Could not get user's default WhatsApp session from database: {}", e.getMessage());
        }
        return null;
    }

    private String getTopicForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailTopic;
            case WHATSAPP -> whatsappTopic;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }

    /**
     * Generate a unique message ID.
     * 
     * ⚠️ COLLISION HANDLING: Retries on database uniqueness constraint violation.
     * UUID collision risk is extremely low, but DB constraint protects us.
     * 
     * @return Unique message ID in format "MSG-{24-char-hex}"
     */
    private String generateMessageId() {
        int maxRetries = 5;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String messageId = "MSG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
            
            // Check if messageId already exists (collision check)
            if (!messageLogRepository.findByMessageId(messageId).isPresent()) {
                return messageId;
            }
            
            log.warn("MessageId collision detected: {}. Retrying (attempt {}/{})", messageId, attempt + 1, maxRetries);
        }
        
        // Fallback: Use timestamp + UUID if all retries fail (extremely unlikely)
        String fallbackId = "MSG-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        log.error("Failed to generate unique messageId after {} retries. Using fallback: {}", maxRetries, fallbackId);
        return fallbackId;
    }

    /**
     * Serialize NotificationPayload from an existing MessageLog.
     * Used for retrying failed Kafka publishes.
     * 
     * @param messageLog The message log to serialize
     * @return Serialized JSON payload
     */
    public String serializeNotificationPayloadFromMessageLog(MessageLog messageLog) {
        try {
            // Fetch site if available
            FrappeSite site = null;
            if (messageLog.getSiteId() != null) {
                site = siteRepository.findById(messageLog.getSiteId()).orElse(null);
            }
            
            return serializeNotificationPayload(
                messageLog.getMessageId(),
                convertMessageLogToRequest(messageLog),
                site,
                null // No session available during retry
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload from message log: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert MessageLog to NotificationRequest for retry purposes.
     */
    private NotificationRequest convertMessageLogToRequest(MessageLog messageLog) {
        NotificationRequest request = new NotificationRequest();
        request.setChannel(messageLog.getChannel());
        request.setRecipient(messageLog.getRecipient());
        request.setSubject(messageLog.getSubject());
        request.setBody(messageLog.getBody());
        request.setImageUrl(messageLog.getImageUrl());
        request.setVideoUrl(messageLog.getVideoUrl());
        request.setDocumentUrl(messageLog.getDocumentUrl());
        request.setFileName(messageLog.getFileName());
        request.setCaption(messageLog.getCaption());
        request.setFromEmail(messageLog.getFromEmail());
        request.setFromName(messageLog.getFromName());
        request.setIsHtml(messageLog.getIsHtml());
        
        // Parse metadata if available
        if (messageLog.getMetadata() != null && !messageLog.getMetadata().trim().isEmpty()) {
            try {
                Map<String, String> metadata = objectMapper.readValue(
                    messageLog.getMetadata(), 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {}
                );
                request.setMetadata(metadata);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse metadata for message {}", messageLog.getMessageId(), e);
            }
        }
        
        return request;
    }

    /**
     * Update message log status.
     * Uses REQUIRES_NEW propagation to ensure it runs in a separate transaction,
     * which is safe to call from Kafka callbacks or async operations.
     * 
     * @param messageId Message ID
     * @param status Delivery status
     * @param errorMessage Error message (if any)
     * @param failureType Failure type (KAFKA or CONSUMER) - only set when status is FAILED
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateMessageLogStatus(String messageId, DeliveryStatus status, String errorMessage, com.clapgrow.notification.api.enums.FailureType failureType) {
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
    
    /**
     * Update message log status (overload without failure_type for non-failure cases).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateMessageLogStatus(String messageId, DeliveryStatus status, String errorMessage) {
        updateMessageLogStatus(messageId, status, errorMessage, null);
    }

}

