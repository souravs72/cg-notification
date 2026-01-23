package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.common.provider.WhatsAppResult;
import com.clapgrow.notification.common.retry.FailureClassification;
import com.clapgrow.notification.whatsapp.enums.DeliveryStatus;
import com.clapgrow.notification.whatsapp.model.NotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * WhatsApp message processing service.
 * 
 * ⚠️ RETRY STRATEGY: Fail-fast consumer
 * 
 * This consumer fails fast and does NOT perform retries or sleep.
 * All retries are handled by KafkaRetryService (producer-side retry authority).
 * 
 * Benefits:
 * - No Thread.sleep() blocking consumer threads
 * - Single retry authority reduces complexity
 * - Better throughput and resource utilization
 * - Consistent retry logic across all channels
 * 
 * Flow:
 * 1. Consumer processes message
 * 2. On failure: Mark as FAILED and acknowledge
 * 3. KafkaRetryService picks up FAILED messages and retries them
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppProcessingService {
    
    private final WasenderService wasenderService;
    private final ObjectMapper objectMapper;
    private final WhatsAppLogService whatsAppLogService;
    private final FailureClassifier failureClassifier;
    
    // ⚠️ METRICS: Metrics are now emitted automatically by MessageStatusHistoryService.appendStatusChange()
    // when status changes. This prevents double-counting and ensures single source of truth.

    @KafkaListener(topics = "notifications-whatsapp")
    public void processWhatsAppNotification(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageId,
            Acknowledgment acknowledgment) {
        
        try {
            NotificationPayload notification = objectMapper.readValue(payload, NotificationPayload.class);
            log.info("Processing WhatsApp notification: {} for recipient: {}", 
                messageId, notification.getRecipient());
            
            // ⚠️ TENANT ISOLATION: siteId is REQUIRED for all messages
            // Fail fast if siteId is missing to prevent bypassing tenant verification
            if (notification.getSiteId() == null) {
                log.error("SECURITY: Message {} rejected - siteId is required for tenant isolation. Payload missing siteId.", messageId);
                handleFailure(messageId, "Tenant isolation violation: siteId is required but missing from payload", null, null);
                acknowledgment.acknowledge();
                return;
            }
            
            // ⚠️ TENANT ISOLATION: Verify payload.siteId matches message_logs.site_id
            // This prevents cross-tenant credential resolution if payload is tampered
            Optional<UUID> messageSiteId = whatsAppLogService.getSiteId(messageId);
            if (messageSiteId.isEmpty()) {
                log.error("Message {} not found in message_logs - cannot verify tenant isolation", messageId);
                handleFailure(messageId, "Message not found in database - tenant verification failed", null, null);
                acknowledgment.acknowledge();
                return;
            }
            if (!notification.getSiteId().equals(messageSiteId.get())) {
                log.error("SECURITY: Tenant isolation violation detected for message {}. Payload siteId={} does not match message_logs site_id={}", 
                    messageId, notification.getSiteId(), messageSiteId.get());
                handleFailure(messageId, "Tenant isolation violation: payload siteId does not match message tenant", null, null);
                acknowledgment.acknowledge();
                return;
            }
            
            // ⚠️ FIXED: Don't set SENT before actually sending - message is already PENDING
            // Send WhatsApp message via WASender
            WhatsAppResult result = wasenderService.sendMessage(notification);
            
            if (result.isSuccess()) {
                // Only set DELIVERED after successful send
                whatsAppLogService.updateStatus(messageId, DeliveryStatus.DELIVERED, null);
                // Metrics are emitted automatically by MessageStatusHistoryService.appendStatusChange()
                log.info("WhatsApp notification {} processed successfully", messageId);
                acknowledgment.acknowledge();
            } else {
                // Build detailed error message
                String errorMessage = result.getErrorMessage();
                String errorDetails = result.errorDetails();
                String responseBody = result.responseBody();
                Integer httpStatusCode = result.httpStatusCode();
                
                // Create comprehensive error message for logging and storage
                StringBuilder fullErrorMessage = new StringBuilder();
                fullErrorMessage.append(errorMessage != null ? errorMessage : "WASender API returned error");
                
                if (errorDetails != null && !errorDetails.isEmpty()) {
                    fullErrorMessage.append("\n\nDetailed Error Information:\n");
                    fullErrorMessage.append(errorDetails);
                }
                
                if (httpStatusCode != null) {
                    fullErrorMessage.append(String.format("\nHTTP Status Code: %d", httpStatusCode));
                }
                // SECURITY: Do not append raw provider response body to stored/linked message - may contain secrets
                // responseBody is still passed to handleFailure for FailureClassifier only; not stored in fullErrorMessage
                
                log.error("Failed to send WhatsApp message {} to recipient {}. Error: {}", 
                    messageId, notification.getRecipient(), errorMessage);
                
                // Fail fast: Mark as FAILED and acknowledge
                // KafkaRetryService will handle retries
                handleFailure(messageId, fullErrorMessage.toString(), httpStatusCode, responseBody);
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error processing WhatsApp notification {}", messageId, e);
            String errorMsg = e.getMessage();
            Integer httpStatusCode = null;
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests) {
                errorMsg = "Rate limit exceeded (429 Too Many Requests)";
                httpStatusCode = 429;
            } else if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                httpStatusCode = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getStatusCode().value();
            }
            
            // Fail fast: Mark as FAILED and acknowledge
            // KafkaRetryService will handle retries
            handleFailure(messageId, errorMsg, httpStatusCode, null);
            
            // ⚠️ CRITICAL: We ACK even on deserialization failures to avoid poison-pill loops.
            // KafkaRetryService handles retries via DB state, not Kafka offsets.
            // If we don't ACK, the same bad message will be redelivered forever, blocking the consumer.
            acknowledgment.acknowledge();
        }
    }

    /**
     * Handle processing failure.
     * 
     * ⚠️ FAIL-FAST STRATEGY: This method marks the message as FAILED and returns immediately.
     * No retries, no Thread.sleep(), no re-queuing.
     * 
     * KafkaRetryService (producer-side) will pick up FAILED messages and retry them.
     * This ensures:
     * - Single retry authority
     * - No blocking in consumer threads
     * - Consistent retry logic across all channels
     * 
     * @param messageId Message ID
     * @param errorMessage Error message
     * @param httpStatusCode HTTP status code (if available)
     * @param responseBody Response body (if available)
     */
    private void handleFailure(String messageId, String errorMessage, 
                              Integer httpStatusCode, String responseBody) {
        // Classify failure using FailureClassifier
        FailureClassification classification = failureClassifier.classify(
            httpStatusCode, errorMessage, responseBody);
        
        // Metrics will be emitted automatically by MessageStatusHistoryService.appendStatusChange()
        // when status is updated to FAILED
        
        String logMessage;
        String errorMessageWithClassification;
        
        switch (classification) {
            case PERMANENT:
                logMessage = "Permanent failure detected for WhatsApp notification {} (invalid API key/auth). " +
                    "Marking as FAILED. KafkaRetryService will handle DLQ routing.";
                errorMessageWithClassification = "Permanent failure (invalid API key/auth): " + errorMessage;
                log.error(logMessage, messageId);
                break;
            case RATE_LIMIT:
                logMessage = "Rate limit failure for WhatsApp notification {}. " +
                    "Marking as FAILED. KafkaRetryService will retry with backoff.";
                errorMessageWithClassification = "Rate limit exceeded: " + errorMessage;
                log.warn(logMessage, messageId);
                break;
            case TRANSIENT:
            default:
                logMessage = "Transient failure for WhatsApp notification {}. " +
                    "Marking as FAILED. KafkaRetryService will retry.";
                errorMessageWithClassification = errorMessage;
                log.warn(logMessage, messageId);
                break;
        }
        
        // Mark as FAILED - KafkaRetryService will handle retries based on classification
        whatsAppLogService.updateStatus(messageId, DeliveryStatus.FAILED, errorMessageWithClassification);
    }
}

