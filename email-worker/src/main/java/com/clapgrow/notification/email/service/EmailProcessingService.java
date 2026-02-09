package com.clapgrow.notification.email.service;

import com.clapgrow.notification.common.provider.EmailProvider;
import com.clapgrow.notification.common.provider.EmailResult;
import com.clapgrow.notification.common.provider.ProviderErrorCategory;
import com.clapgrow.notification.email.model.NotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Email message processing service.
 * 
 * ⚠️ RETRY STRATEGY: Fail-fast consumer (consistent with WhatsApp consumer)
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
 * 2. On failure: Mark as FAILED (CONSUMER) and acknowledge
 * 3. KafkaRetryService picks up FAILED messages and retries them
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingService {
    
    private final EmailProvider<NotificationPayload> emailProvider;
    private final ObjectMapper objectMapper;
    private final EmailLogService emailLogService;
    
    // ⚠️ METRICS: Metrics are now emitted automatically by MessageStatusHistoryService.appendStatusChange()
    // when status changes. This prevents double-counting and ensures single source of truth.

    @KafkaListener(topics = "notifications-email")
    public void processEmailNotification(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageId,
            Acknowledgment acknowledgment) {
        
        try {
            NotificationPayload notification = objectMapper.readValue(payload, NotificationPayload.class);
            log.info("Processing email notification: {} for recipient: {}, payload.siteId={}", 
                messageId, notification.getRecipient(), notification.getSiteId());
            
            // ⚠️ TENANT ISOLATION: siteId handling
            // If siteId is provided in payload, it MUST match message_logs.site_id for tenant isolation.
            // If siteId is null in payload, message_logs.site_id must also be null (dashboard message = use global config).
            // getMessageSiteLookup distinguishes "row not found" from "row found with site_id=null".
            EmailLogService.MessageSiteLookup lookup = emailLogService.getMessageSiteLookup(messageId);
            if (!lookup.found()) {
                log.error("Message {} not found in message_logs - cannot verify tenant isolation", messageId);
                handleFailure(messageId, "Message not found in database - tenant verification failed", ProviderErrorCategory.CONFIG);
                acknowledgment.acknowledge();
                return;
            }

            UUID messageSiteId = lookup.siteId();
            UUID payloadSiteId = notification.getSiteId();

            // If payload has siteId, it must match message_logs.site_id
            if (payloadSiteId != null) {
                if (!payloadSiteId.equals(messageSiteId)) {
                    log.error("SECURITY: Tenant isolation violation detected for message {}. Payload siteId={} does not match message_logs site_id={}",
                        messageId, payloadSiteId, messageSiteId);
                    handleFailure(messageId, "Tenant isolation violation: payload siteId does not match message tenant", ProviderErrorCategory.CONFIG);
                    acknowledgment.acknowledge();
                    return;
                }
            } else {
                // If payload siteId is null, message_logs.site_id must also be null (dashboard message)
                if (messageSiteId != null) {
                    log.error("SECURITY: Tenant isolation violation detected for message {}. Payload siteId is null but message_logs site_id={}",
                        messageId, messageSiteId);
                    handleFailure(messageId, "Tenant isolation violation: payload siteId is null but message has tenant", ProviderErrorCategory.CONFIG);
                    acknowledgment.acknowledge();
                    return;
                }
                log.debug("Message {} has no siteId - will use global SendGrid config", messageId);
            }
            
            // ⚠️ FIXED: Don't set SENT before actually sending - message is already PENDING
            // Send email via provider (provider-agnostic)
            EmailResult result = emailProvider.sendEmail(notification);
            
            if (result.isSuccess()) {
                // Only set DELIVERED after successful send
                emailLogService.updateStatus(messageId, "DELIVERED", null);
                // Metrics are emitted automatically by MessageStatusHistoryService.appendStatusChange()
                log.info("Email notification {} processed successfully", messageId);
                acknowledgment.acknowledge();
            } else {
                // Build detailed error message
                String errorMessage = result.getErrorMessage();
                
                log.error("Failed to send email message {} to recipient {} via {}. Error: {}", 
                    messageId, notification.getRecipient(), emailProvider.getProviderName(), errorMessage);
                
                // Fail fast: Mark as FAILED and acknowledge
                // KafkaRetryService will handle retries based on error category
                handleFailure(messageId, errorMessage, result.getErrorCategory());
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error processing email notification {}", messageId, e);
            String errorMsg = e.getMessage();
            if (errorMsg == null) {
                errorMsg = e.getClass().getSimpleName() + ": " + e.toString();
            }
            
            // Fail fast: Mark as FAILED and acknowledge
            // KafkaRetryService will handle retries
            // Treat unexpected exceptions as TEMPORARY (may be transient)
            handleFailure(messageId, errorMsg, ProviderErrorCategory.TEMPORARY);
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
     * Uses error category to determine if failure is permanent or temporary.
     * 
     * @param messageId Message ID
     * @param errorMessage Error message
     * @param errorCategory Error category (null if not available)
     */
    private void handleFailure(String messageId, String errorMessage, ProviderErrorCategory errorCategory) {
        // Determine if failure is permanent based on error category
        boolean isPermanentFailure = errorCategory == ProviderErrorCategory.PERMANENT ||
                                     errorCategory == ProviderErrorCategory.AUTH ||
                                     errorCategory == ProviderErrorCategory.CONFIG ||
                                     // Fallback: check error message for known permanent failures
                                     (errorCategory == null && errorMessage != null && 
                                      (errorMessage.contains("invalid, expired, or revoked") || 
                                       errorMessage.contains("401") ||
                                       errorMessage.contains("does not match a verified Sender Identity") ||
                                       errorMessage.contains("403") ||
                                       errorMessage.contains("unauthorized") ||
                                       errorMessage.contains("authentication")));
        
        // Metrics will be emitted automatically by MessageStatusHistoryService.appendStatusChange()
        // when status is updated to FAILED
        
        if (isPermanentFailure) {
            // Permanent failures: Mark as FAILED immediately
            // KafkaRetryService will check retry count and send to DLQ if needed
            log.error("Permanent failure detected for email notification {} (category: {}). " +
                "Marking as FAILED. KafkaRetryService will handle DLQ routing.", 
                messageId, errorCategory != null ? errorCategory : "unknown");
            emailLogService.updateStatus(messageId, "FAILED", 
                "Permanent failure: " + errorMessage);
        } else {
            // Transient failures: Mark as FAILED
            // KafkaRetryService will retry based on retry count and error category
            log.warn("Transient failure for email notification {} (category: {}). " +
                "Marking as FAILED. KafkaRetryService will retry.", 
                messageId, errorCategory != null ? errorCategory : "unknown");
            emailLogService.updateStatus(messageId, "FAILED", errorMessage);
        }
    }
}

