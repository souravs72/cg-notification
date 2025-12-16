package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.whatsapp.model.NotificationPayload;
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
public class WhatsAppProcessingService {
    
    private static final String DLQ_TOPIC = "notifications-whatsapp-dlq";
    private static final int MAX_RETRIES = 3;
    
    private final WasenderService wasenderService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final WhatsAppLogService whatsAppLogService;

    @KafkaListener(topics = "notifications-whatsapp", groupId = "whatsapp-worker-group")
    public void processWhatsAppNotification(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String messageId,
            Acknowledgment acknowledgment) {
        
        try {
            NotificationPayload notification = objectMapper.readValue(payload, NotificationPayload.class);
            log.info("Processing WhatsApp notification: {} for recipient: {}", 
                messageId, notification.getRecipient());
            
            // Update status to SENT
            whatsAppLogService.updateStatus(messageId, "SENT", null);
            
            // Send WhatsApp message via WASender
            WasenderSendResult result = wasenderService.sendMessage(notification);
            
            if (result.isSuccess()) {
                whatsAppLogService.updateStatus(messageId, "DELIVERED", null);
                log.info("WhatsApp notification {} processed successfully", messageId);
            } else {
                // Get retry count from database to track retries properly
                int currentRetryCount = whatsAppLogService.getRetryCount(messageId);
                // Build detailed error message
                String errorMessage = result.getErrorMessage();
                String errorDetails = result.getErrorDetails();
                
                // Create comprehensive error message for logging and storage
                StringBuilder fullErrorMessage = new StringBuilder();
                fullErrorMessage.append(errorMessage != null ? errorMessage : "WASender API returned error");
                
                if (errorDetails != null && !errorDetails.isEmpty()) {
                    fullErrorMessage.append("\n\nDetailed Error Information:\n");
                    fullErrorMessage.append(errorDetails);
                }
                
                if (result.getHttpStatusCode() != null) {
                    fullErrorMessage.append(String.format("\nHTTP Status Code: %d", result.getHttpStatusCode()));
                }
                
                if (result.getResponseBody() != null && !result.getResponseBody().isEmpty()) {
                    fullErrorMessage.append(String.format("\nAPI Response Body: %s", result.getResponseBody()));
                }
                
                log.error("Failed to send WhatsApp message {} to recipient {}. Error: {}\nDetails: {}", 
                    messageId, notification.getRecipient(), errorMessage, errorDetails);
                
                handleFailure(messageId, payload, currentRetryCount, fullErrorMessage.toString());
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing WhatsApp notification {}", messageId, e);
            int currentRetryCount = whatsAppLogService.getRetryCount(messageId);
            String errorMsg = e.getMessage();
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests) {
                errorMsg = "Rate limit exceeded (429 Too Many Requests)";
            }
            handleFailure(messageId, payload, currentRetryCount, errorMsg);
            acknowledgment.acknowledge();
        }
    }

    private void handleFailure(String messageId, String payload, int retryCount, String errorMessage) {
        if (retryCount < MAX_RETRIES) {
            // Check if it's a rate limit error (429) - use longer backoff
            boolean isRateLimit = errorMessage != null && 
                (errorMessage.contains("429") || errorMessage.contains("Too Many Requests"));
            
            long backoffMs = isRateLimit 
                ? 5000L * (retryCount + 1) // 5s, 10s, 15s for rate limits
                : 2000L * (retryCount + 1); // 2s, 4s, 6s for other errors
            
            log.warn("Retrying WhatsApp notification {} (attempt {}/{}) after {}ms. Error: {}", 
                messageId, retryCount + 1, MAX_RETRIES, backoffMs, errorMessage);
            
            whatsAppLogService.updateStatus(messageId, "PENDING", errorMessage);
            whatsAppLogService.incrementRetryCount(messageId);
            
            try {
                Thread.sleep(backoffMs);
                kafkaTemplate.send("notifications-whatsapp", messageId, payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while retrying WhatsApp notification {}", messageId);
                sendToDLQ(messageId, payload, errorMessage);
            }
        } else {
            log.error("Max retries reached for WhatsApp notification {}. Sending to DLQ", messageId);
            sendToDLQ(messageId, payload, errorMessage);
            whatsAppLogService.updateStatus(messageId, "FAILED", 
                "Max retries exceeded: " + errorMessage);
        }
    }

    private void sendToDLQ(String messageId, String payload, String errorMessage) {
        try {
            String dlqPayload = payload + "|ERROR:" + errorMessage;
            kafkaTemplate.send(DLQ_TOPIC, messageId, dlqPayload);
            log.info("Sent failed WhatsApp notification {} to DLQ", messageId);
        } catch (Exception e) {
            log.error("Failed to send message {} to DLQ", messageId, e);
        }
    }
}

