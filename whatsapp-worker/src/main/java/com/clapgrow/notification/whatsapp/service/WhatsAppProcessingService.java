package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.common.provider.WhatsAppResult;
import com.clapgrow.notification.common.retry.FailureClassification;
import com.clapgrow.notification.whatsapp.enums.DeliveryStatus;
import com.clapgrow.notification.whatsapp.model.NotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * WhatsApp message processing service (SQS consumer).
 *
 * ⚠️ RETRY STRATEGY: Fail-fast. On failure we mark FAILED and delete message; MessagingRetryService republishes.
 * ⚠️ IDEMPOTENCY: If message_logs already has status DELIVERED, skip and delete (SQS Standard can deliver duplicates).
 * ⚠️ WaSender SEQUENTIAL RULE: One message per session at a time; mandatory delay between messages.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppProcessingService {

    private final WasenderService wasenderService;
    private final ObjectMapper objectMapper;
    private final WhatsAppLogService whatsAppLogService;
    private final FailureClassifier failureClassifier;
    private final WhatsAppSessionSequencer sessionSequencer;

    @SqsListener(value = "${messaging.sqs.queues.whatsapp}")
    public void processWhatsAppNotification(
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

            Optional<String> status = whatsAppLogService.getStatus(messageId);
            if (status.isPresent() && "DELIVERED".equals(status.get())) {
                log.debug("Message {} already DELIVERED, skipping (idempotent)", messageId);
                return;
            }

            log.info("Processing WhatsApp notification: {} for recipient: {}", messageId, notification.getRecipient());

            if (notification.getSiteId() == null) {
                log.error("SECURITY: Message {} rejected - siteId is required for tenant isolation.", messageId);
                handleFailure(messageId, "Tenant isolation violation: siteId is required but missing from payload", null, null);
                return;
            }

            Optional<UUID> messageSiteId = whatsAppLogService.getSiteId(messageId);
            if (messageSiteId.isEmpty()) {
                log.error("Message {} not found in message_logs - cannot verify tenant isolation", messageId);
                handleFailure(messageId, "Message not found in database - tenant verification failed", null, null);
                return;
            }
            if (!notification.getSiteId().equals(messageSiteId.get())) {
                log.error("SECURITY: Tenant isolation violation for message {}. Payload siteId={} != message_logs site_id={}",
                    messageId, notification.getSiteId(), messageSiteId.get());
                handleFailure(messageId, "Tenant isolation violation: payload siteId does not match message tenant", null, null);
                return;
            }

            String sessionKey = WhatsAppSessionSequencer.deriveSessionKey(
                notification.getWhatsappSessionName(), notification.getSiteId());
            WhatsAppResult result = sessionSequencer.executeForSession(sessionKey, () ->
                wasenderService.sendMessage(notification));

            if (result.isSuccess()) {
                whatsAppLogService.updateStatus(messageId, DeliveryStatus.DELIVERED, null);
                log.info("WhatsApp notification {} processed successfully", messageId);
            } else {
                String errorMessage = result.getErrorMessage();
                String errorDetails = result.errorDetails();
                Integer httpStatusCode = result.httpStatusCode();
                StringBuilder fullErrorMessage = new StringBuilder();
                fullErrorMessage.append(errorMessage != null ? errorMessage : "WASender API returned error");
                if (errorDetails != null && !errorDetails.isEmpty()) {
                    fullErrorMessage.append("\n\nDetailed Error Information:\n").append(errorDetails);
                }
                if (httpStatusCode != null) {
                    fullErrorMessage.append(String.format("\nHTTP Status Code: %d", httpStatusCode));
                }
                log.error("Failed to send WhatsApp message {} to recipient {}. Error: {}",
                    messageId, notification.getRecipient(), errorMessage);
                handleFailure(messageId, fullErrorMessage.toString(), httpStatusCode, result.responseBody());
            }
        } catch (Exception e) {
            if (messageId == null) messageId = sqsMessageId;
            log.error("Error processing WhatsApp notification {}", messageId, e);
            String errorMsg = e.getMessage();
            Integer httpStatusCode = null;
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests) {
                errorMsg = "Rate limit exceeded (429 Too Many Requests)";
                httpStatusCode = 429;
            } else if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                httpStatusCode = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getStatusCode().value();
            }
            handleFailure(messageId, errorMsg != null ? errorMsg : e.getClass().getSimpleName(), httpStatusCode, null);
        }
    }

    private void handleFailure(String messageId, String errorMessage,
                              Integer httpStatusCode, String responseBody) {
        FailureClassification classification = failureClassifier.classify(httpStatusCode, errorMessage, responseBody);
        String errorMessageWithClassification;
        switch (classification) {
            case PERMANENT:
                errorMessageWithClassification = "Permanent failure (invalid API key/auth): " + errorMessage;
                log.error("Permanent failure for WhatsApp {}. Marking as FAILED. MessagingRetryService will handle DLQ.", messageId);
                break;
            case RATE_LIMIT:
                errorMessageWithClassification = "Rate limit exceeded: " + errorMessage;
                log.warn("Rate limit failure for WhatsApp {}. Marking as FAILED. MessagingRetryService will retry.", messageId);
                break;
            case TRANSIENT:
            default:
                errorMessageWithClassification = errorMessage;
                log.warn("Transient failure for WhatsApp {}. Marking as FAILED. MessagingRetryService will retry.", messageId);
                break;
        }
        whatsAppLogService.updateStatus(messageId, DeliveryStatus.FAILED, errorMessageWithClassification);
    }
}

