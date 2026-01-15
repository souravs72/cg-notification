package com.clapgrow.notification.email.service;

import com.clapgrow.notification.email.model.NotificationPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingService {
    
    private static final String DLQ_TOPIC = "notifications-email-dlq";
    private static final int MAX_RETRIES = 3;
    
    private final SendGridService sendGridService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final EmailLogService emailLogService;

    @KafkaListener(topics = "notifications-email", groupId = "email-worker-group")
    public void processEmailNotification(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageId,
            Acknowledgment acknowledgment) {
        
        try {
            NotificationPayload notification = objectMapper.readValue(payload, NotificationPayload.class);
            log.info("Processing email notification: {} for recipient: {}", 
                messageId, notification.getRecipient());
            
            // Update status to SENT
            emailLogService.updateStatus(messageId, "SENT", null);
            
            // Send email via SendGrid
            SendEmailResult result = sendGridService.sendEmail(notification);
            
            if (result.isSuccess()) {
                emailLogService.updateStatus(messageId, "DELIVERED", null);
                log.info("Email notification {} processed successfully", messageId);
                acknowledgment.acknowledge();
            } else {
                // Get current retry count from database
                int currentRetryCount = emailLogService.getRetryCount(messageId);
                handleFailure(messageId, payload, currentRetryCount, result.getErrorMessage());
                // Acknowledge after handling failure (will retry via Kafka if needed)
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error processing email notification {}", messageId, e);
            int currentRetryCount = emailLogService.getRetryCount(messageId);
            handleFailure(messageId, payload, currentRetryCount, e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private void handleFailure(String messageId, String payload, int retryCount, String errorMessage) {
        // Check for permanent failures (invalid API key, unverified sender) - fail faster
        boolean isPermanentFailure = errorMessage != null && 
            (errorMessage.contains("invalid, expired, or revoked") || 
             errorMessage.contains("401") ||
             errorMessage.contains("does not match a verified Sender Identity") ||
             errorMessage.contains("403"));
        
        if (isPermanentFailure && retryCount >= 1) {
            // Don't retry permanent failures more than once
            log.error("Permanent failure detected for email notification {} (invalid API key/unverified sender). Marking as FAILED immediately.", messageId);
            sendToDLQ(messageId, payload, errorMessage);
            emailLogService.updateStatus(messageId, "FAILED", 
                "Permanent failure (invalid API key/unverified sender): " + errorMessage);
            return;
        }
        
        if (retryCount < MAX_RETRIES) {
            log.warn("Retrying email notification {} (attempt {}/{})", 
                messageId, retryCount + 1, MAX_RETRIES);
            
            // Set status to PENDING for retry
            emailLogService.updateStatus(messageId, "PENDING", errorMessage);
            
            // Exponential backoff with longer delay for API errors
            long backoffMs = 2000L * (retryCount + 1); // 2s, 4s, 6s
            
            try {
                Thread.sleep(backoffMs);
                kafkaTemplate.send("notifications-email", messageId, payload);
                // Only increment retry count after successful Kafka send
                emailLogService.incrementRetryCount(messageId);
                log.info("Re-queued email notification {} for retry", messageId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while retrying email notification {}", messageId);
                sendToDLQ(messageId, payload, errorMessage);
                emailLogService.updateStatus(messageId, "FAILED", 
                    "Retry interrupted: " + errorMessage);
            } catch (Exception e) {
                // If Kafka send fails, don't increment retry count and send to DLQ
                log.error("Failed to re-queue email notification {} for retry", messageId, e);
                sendToDLQ(messageId, payload, errorMessage);
                emailLogService.updateStatus(messageId, "FAILED", 
                    "Failed to re-queue: " + errorMessage);
            }
        } else {
            log.error("Max retries ({}) reached for email notification {}. Sending to DLQ", 
                MAX_RETRIES, messageId);
            sendToDLQ(messageId, payload, errorMessage);
            emailLogService.updateStatus(messageId, "FAILED", 
                "Max retries exceeded: " + errorMessage);
        }
    }

    private void sendToDLQ(String messageId, String payload, String errorMessage) {
        try {
            String dlqPayload = payload + "|ERROR:" + errorMessage;
            kafkaTemplate.send(DLQ_TOPIC, messageId, dlqPayload);
            log.info("Sent failed email notification {} to DLQ", messageId);
        } catch (Exception e) {
            log.error("Failed to send message {} to DLQ", messageId, e);
        }
    }
}

