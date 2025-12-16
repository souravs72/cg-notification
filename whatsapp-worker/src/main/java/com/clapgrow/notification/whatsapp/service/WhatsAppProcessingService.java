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
                String responseBody = result.getResponseBody();
                Integer httpStatusCode = result.getHttpStatusCode();
                
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
                
                if (responseBody != null && !responseBody.isEmpty()) {
                    fullErrorMessage.append(String.format("\nAPI Response Body: %s", responseBody));
                }
                
                log.error("Failed to send WhatsApp message {} to recipient {}. Error: {}\nDetails: {}", 
                    messageId, notification.getRecipient(), errorMessage, errorDetails);
                
                handleFailure(messageId, payload, currentRetryCount, fullErrorMessage.toString(), 
                    httpStatusCode, responseBody);
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing WhatsApp notification {}", messageId, e);
            int currentRetryCount = whatsAppLogService.getRetryCount(messageId);
            String errorMsg = e.getMessage();
            Integer httpStatusCode = null;
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests) {
                errorMsg = "Rate limit exceeded (429 Too Many Requests)";
                httpStatusCode = 429;
            } else if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                httpStatusCode = ((org.springframework.web.reactive.function.client.WebClientResponseException) e).getStatusCode().value();
            }
            handleFailure(messageId, payload, currentRetryCount, errorMsg, httpStatusCode, null);
            acknowledgment.acknowledge();
        }
    }

    private void handleFailure(String messageId, String payload, int retryCount, String errorMessage) {
        handleFailure(messageId, payload, retryCount, errorMessage, null, null);
    }
    
    private void handleFailure(String messageId, String payload, int retryCount, String errorMessage, 
                              Integer httpStatusCode, String responseBody) {
        // Check for permanent failures (invalid API key, authentication errors) - fail immediately
        boolean isPermanentFailure = false;
        
        // Check HTTP status code first (401 = unauthorized)
        if (httpStatusCode != null && httpStatusCode == 401) {
            isPermanentFailure = true;
        }
        
        // Check error message and response body for authentication errors
        if (!isPermanentFailure && errorMessage != null) {
            String lowerError = errorMessage.toLowerCase();
            isPermanentFailure = lowerError.contains("invalid api key") || 
                               lowerError.contains("401") || 
                               lowerError.contains("unauthorized") ||
                               lowerError.contains("authentication");
        }
        
        // Check response body for "Invalid API key"
        if (!isPermanentFailure && responseBody != null) {
            String lowerResponse = responseBody.toLowerCase();
            isPermanentFailure = lowerResponse.contains("invalid api key") || 
                               lowerResponse.contains("invalid") && lowerResponse.contains("key");
        }
        
        if (isPermanentFailure && retryCount >= 1) {
            // Don't retry permanent failures more than once
            log.error("Permanent failure detected for WhatsApp notification {} (invalid API key/auth). Marking as FAILED immediately.", messageId);
            sendToDLQ(messageId, payload, errorMessage);
            whatsAppLogService.updateStatus(messageId, "FAILED", 
                "Permanent failure (invalid API key/auth): " + errorMessage);
            return;
        }
        
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
                log.info("Re-queued WhatsApp notification {} for retry", messageId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while retrying WhatsApp notification {}", messageId);
                sendToDLQ(messageId, payload, errorMessage);
                whatsAppLogService.updateStatus(messageId, "FAILED", 
                    "Retry interrupted: " + errorMessage);
            }
        } else {
            log.error("Max retries ({}) reached for WhatsApp notification {}. Sending to DLQ", 
                MAX_RETRIES, messageId);
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

