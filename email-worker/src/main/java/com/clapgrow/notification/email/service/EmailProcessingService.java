package com.clapgrow.notification.email.service;

import com.clapgrow.notification.common.provider.EmailProvider;
import com.clapgrow.notification.common.provider.EmailResult;
import com.clapgrow.notification.common.provider.ProviderErrorCategory;
import com.clapgrow.notification.email.model.NotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Email message processing service (SQS consumer).
 *
 * ⚠️ RETRY STRATEGY: Fail-fast. On failure we mark FAILED and delete message; MessagingRetryService republishes.
 * ⚠️ IDEMPOTENCY: If message_logs already has status DELIVERED, skip and delete (SQS Standard can deliver duplicates).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingService {

    private final EmailProvider<NotificationPayload> emailProvider;
    private final ObjectMapper objectMapper;
    private final EmailLogService emailLogService;

    @SqsListener(value = "${messaging.sqs.queues.email}")
    public void processEmailNotification(
            String payload,
            @Header(name = "MessageId", required = false) String sqsMessageId) {
        String messageId = null;
        try {
            NotificationPayload notification = objectMapper.readValue(payload, NotificationPayload.class);
            messageId = notification.getMessageId();
            if (messageId == null) messageId = sqsMessageId;

            if (messageId == null) {
                log.error("Cannot process message: no messageId in payload or SQS MessageId");
                throw new IllegalArgumentException("Missing messageId");
            }

            // Idempotency: skip if already delivered (SQS can deliver duplicates)
            Optional<String> status = emailLogService.getStatus(messageId);
            if (status.isPresent() && "DELIVERED".equals(status.get())) {
                log.debug("Message {} already DELIVERED, skipping (idempotent)", messageId);
                return;
            }

            log.info("Processing email notification: {} for recipient: {}, payload.siteId={}",
                messageId, notification.getRecipient(), notification.getSiteId());

            EmailLogService.MessageSiteLookup lookup = emailLogService.getMessageSiteLookup(messageId);
            if (!lookup.found()) {
                log.error("Message {} not found in message_logs - cannot verify tenant isolation", messageId);
                handleFailure(messageId, "Message not found in database - tenant verification failed", ProviderErrorCategory.CONFIG);
                return;
            }

            UUID messageSiteId = lookup.siteId();
            UUID payloadSiteId = notification.getSiteId();

            if (payloadSiteId != null) {
                if (!payloadSiteId.equals(messageSiteId)) {
                    log.error("SECURITY: Tenant isolation violation for message {}. Payload siteId={} != message_logs site_id={}",
                        messageId, payloadSiteId, messageSiteId);
                    handleFailure(messageId, "Tenant isolation violation: payload siteId does not match message tenant", ProviderErrorCategory.CONFIG);
                    return;
                }
            } else {
                if (messageSiteId != null) {
                    log.error("SECURITY: Tenant isolation violation for message {}. Payload siteId null but message_logs site_id={}", messageId, messageSiteId);
                    handleFailure(messageId, "Tenant isolation violation: payload siteId is null but message has tenant", ProviderErrorCategory.CONFIG);
                    return;
                }
                log.debug("Message {} has no siteId - will use global SendGrid config", messageId);
            }

            EmailResult result = emailProvider.sendEmail(notification);

            if (result.isSuccess()) {
                emailLogService.updateStatus(messageId, "DELIVERED", null);
                log.info("Email notification {} processed successfully", messageId);
            } else {
                log.error("Failed to send email message {} to recipient {} via {}. Error: {}",
                    messageId, notification.getRecipient(), emailProvider.getProviderName(), result.getErrorMessage());
                handleFailure(messageId, result.getErrorMessage(), result.getErrorCategory());
            }
        } catch (Exception e) {
            if (messageId == null) messageId = sqsMessageId;
            log.error("Error processing email notification {}", messageId, e);
            String errorMsg = e.getMessage();
            if (errorMsg == null) errorMsg = e.getClass().getSimpleName() + ": " + e.toString();
            handleFailure(messageId, errorMsg, ProviderErrorCategory.TEMPORARY);
        }
    }

    private void handleFailure(String messageId, String errorMessage, ProviderErrorCategory errorCategory) {
        boolean isPermanentFailure = errorCategory == ProviderErrorCategory.PERMANENT ||
            errorCategory == ProviderErrorCategory.AUTH ||
            errorCategory == ProviderErrorCategory.CONFIG ||
            (errorCategory == null && errorMessage != null &&
                (errorMessage.contains("invalid, expired, or revoked") ||
                    errorMessage.contains("401") ||
                    errorMessage.contains("does not match a verified Sender Identity") ||
                    errorMessage.contains("403") ||
                    errorMessage.contains("unauthorized") ||
                    errorMessage.contains("authentication")));

        if (isPermanentFailure) {
            log.error("Permanent failure for email {} (category: {}). Marking as FAILED. MessagingRetryService will handle DLQ.", messageId, errorCategory != null ? errorCategory : "unknown");
            emailLogService.updateStatus(messageId, "FAILED", "Permanent failure: " + errorMessage);
        } else {
            log.warn("Transient failure for email {} (category: {}). Marking as FAILED. MessagingRetryService will retry.", messageId, errorCategory != null ? errorCategory : "unknown");
            emailLogService.updateStatus(messageId, "FAILED", errorMessage);
        }
    }
}
