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
            } else {
                handleFailure(messageId, payload, 0, result.getErrorMessage());
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing email notification {}", messageId, e);
            handleFailure(messageId, payload, 0, e.getMessage());
            acknowledgment.acknowledge();
        }
    }

    private void handleFailure(String messageId, String payload, int retryCount, String errorMessage) {
        if (retryCount < MAX_RETRIES) {
            log.warn("Retrying email notification {} (attempt {}/{})", 
                messageId, retryCount + 1, MAX_RETRIES);
            
            emailLogService.updateStatus(messageId, "FAILED", errorMessage);
            emailLogService.incrementRetryCount(messageId);
            
            // In production, you might want to use a delay topic or retry mechanism
            // For now, we'll send to DLQ after max retries
            try {
                Thread.sleep(1000 * (retryCount + 1)); // Exponential backoff
                kafkaTemplate.send("notifications-email", messageId, payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sendToDLQ(messageId, payload, errorMessage);
            }
        } else {
            log.error("Max retries reached for email notification {}. Sending to DLQ", messageId);
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

