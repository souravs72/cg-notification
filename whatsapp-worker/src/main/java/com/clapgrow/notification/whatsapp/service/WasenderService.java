package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.whatsapp.model.NotificationPayload;
import com.clapgrow.notification.whatsapp.model.WasenderMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    public WasenderSendResult sendMessage(NotificationPayload payload) {
        String originalRecipient = payload.getRecipient();
        String apiKey = payload.getWasenderApiKey();
        WasenderMessageRequest request = null;
        String requestUrl = wasenderBaseUrl + "/send-message";
        
        try {
            // Get API key from payload, fallback to environment variable for backward compatibility
            if (apiKey == null || apiKey.trim().isEmpty()) {
                String errorMsg = "WASender API key is not provided in the payload. Please configure it first.";
                log.error("WASender API key is not provided in the payload for recipient: {}", originalRecipient);
                return WasenderSendResult.failure(errorMsg, 
                    String.format("Missing API key for recipient: %s. URL: %s", originalRecipient, requestUrl));
            }
            
            request = buildWasenderRequest(payload);
            
            // Log request details (without sensitive data)
            try {
                String requestJson = objectMapper.writeValueAsString(request);
                log.info("Sending WhatsApp message to WASender API. URL: {}, Recipient: {}, Request: {}", 
                    requestUrl, request.getTo(), requestJson);
            } catch (Exception e) {
                log.warn("Failed to serialize request for logging: {}", e.getMessage());
                log.info("Sending WhatsApp message to WASender API. URL: {}, Recipient: {}", 
                    requestUrl, request.getTo() != null ? request.getTo() : originalRecipient);
            }
            
            String response = webClient.post()
                .uri(requestUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            log.info("WhatsApp message sent successfully to {}. WASender API response: {}", 
                request.getTo(), response != null ? (response.length() > 500 ? response.substring(0, 500) + "..." : response) : "null");
            return WasenderSendResult.success();
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests e) {
            String errorBody = e.getResponseBodyAsString();
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            String errorMsg = String.format("Rate limit exceeded (429 Too Many Requests) for recipient: %s", recipient);
            String errorDetails = String.format(
                "HTTP Status: 429 Too Many Requests%n" +
                "URL: %s%n" +
                "Recipient: %s%n" +
                "Response Body: %s%n" +
                "Request Details: to=%s, session=%s",
                requestUrl,
                recipient,
                errorBody != null ? errorBody : "null",
                request != null ? request.getTo() : originalRecipient,
                request != null ? request.getWhatsappSession() : "N/A"
            );
            log.warn("Rate limit exceeded (429) for WhatsApp message to {}. Response body: {}", 
                recipient, errorBody);
            return WasenderSendResult.failure(errorMsg, errorDetails, 429, errorBody);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            String errorMsg = String.format("HTTP error %s sending WhatsApp message to %s via WASender API", 
                e.getStatusCode(), recipient);
            String errorDetails = String.format(
                "HTTP Status: %s (%s)%n" +
                "URL: %s%n" +
                "Recipient: %s%n" +
                "Response Body: %s%n" +
                "Request Details: to=%s, session=%s, hasText=%s, hasImage=%s, hasVideo=%s, hasDocument=%s",
                e.getStatusCode(),
                e.getStatusCode().toString(),
                requestUrl,
                recipient,
                errorBody != null ? errorBody : "null",
                request != null ? request.getTo() : originalRecipient,
                request != null ? request.getWhatsappSession() : "N/A",
                request != null && request.getText() != null,
                request != null && request.getImageUrl() != null,
                request != null && request.getVideoUrl() != null,
                request != null && request.getDocumentUrl() != null
            );
            log.error("HTTP error {} sending WhatsApp message to {} via WASender API ({}). " +
                    "Response body: {}. Request details: to={}, session={}, hasText={}, hasImage={}, hasVideo={}, hasDocument={}",
                e.getStatusCode(), 
                recipient,
                requestUrl,
                errorBody != null ? errorBody : "null",
                request != null ? request.getTo() : originalRecipient,
                request != null ? request.getWhatsappSession() : "N/A",
                request != null && request.getText() != null,
                request != null && request.getImageUrl() != null,
                request != null && request.getVideoUrl() != null,
                request != null && request.getDocumentUrl() != null);
            return WasenderSendResult.failure(errorMsg, errorDetails, e.getStatusCode().value(), errorBody);
        } catch (Exception e) {
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            String errorMsg = String.format("Unexpected error sending WhatsApp message via WASender to %s: %s", 
                recipient, e.getClass().getSimpleName());
            String errorDetails = String.format(
                "Error Class: %s%n" +
                "Error Message: %s%n" +
                "URL: %s%n" +
                "Recipient: %s%n" +
                "Request Details: to=%s, session=%s",
                e.getClass().getName(),
                e.getMessage() != null ? e.getMessage() : "null",
                requestUrl,
                recipient,
                request != null ? request.getTo() : originalRecipient,
                request != null ? request.getWhatsappSession() : "N/A"
            );
            log.error("Unexpected error sending WhatsApp message via WASender to {}: {}. " +
                    "Error class: {}, Message: {}",
                recipient,
                e.getClass().getSimpleName(),
                e.getClass().getName(),
                e.getMessage(),
                e);
            return WasenderSendResult.failure(errorMsg, errorDetails);
        }
    }

    private WasenderMessageRequest buildWasenderRequest(NotificationPayload payload) {
        WasenderMessageRequest request = new WasenderMessageRequest();
        // Clean recipient phone number/email - remove control characters and trim
        String originalRecipient = payload.getRecipient();
        String cleanedRecipient = cleanRecipient(originalRecipient);
        
        if (!originalRecipient.equals(cleanedRecipient)) {
            log.debug("Cleaned recipient from '{}' to '{}'", originalRecipient, cleanedRecipient);
        }
        
        request.setTo(cleanedRecipient);
        
        // Set WhatsApp session name if provided
        if (payload.getWhatsappSessionName() != null && !payload.getWhatsappSessionName().trim().isEmpty()) {
            request.setWhatsappSession(payload.getWhatsappSessionName());
        }
        
        // Set replyTo if provided (for quoted messages)
        if (payload.getMetadata() != null && payload.getMetadata().containsKey("replyTo")) {
            request.setReplyTo(payload.getMetadata().get("replyTo"));
        }
        
        // Determine message type based on available fields (order matters - check most specific first)
        if (payload.getImageUrl() != null && !payload.getImageUrl().isEmpty()) {
            // Image message
            request.setImageUrl(payload.getImageUrl());
            if (payload.getCaption() != null && !payload.getCaption().isEmpty()) {
                request.setText(payload.getCaption());
            } else if (payload.getBody() != null && !payload.getBody().isEmpty()) {
                request.setText(payload.getBody());
            }
        } else if (payload.getVideoUrl() != null && !payload.getVideoUrl().isEmpty()) {
            // Video message
            request.setVideoUrl(payload.getVideoUrl());
            if (payload.getCaption() != null && !payload.getCaption().isEmpty()) {
                request.setText(payload.getCaption());
            } else if (payload.getBody() != null && !payload.getBody().isEmpty()) {
                request.setText(payload.getBody());
            }
        } else if (payload.getDocumentUrl() != null && !payload.getDocumentUrl().isEmpty()) {
            // Document message
            request.setDocumentUrl(payload.getDocumentUrl());
            if (payload.getFileName() != null && !payload.getFileName().isEmpty()) {
                request.setFileName(payload.getFileName());
            }
            if (payload.getCaption() != null && !payload.getCaption().isEmpty()) {
                request.setText(payload.getCaption());
            } else if (payload.getBody() != null && !payload.getBody().isEmpty()) {
                request.setText(payload.getBody());
            }
        } else if (payload.getMetadata() != null && payload.getMetadata().containsKey("audioUrl")) {
            // Audio message
            String audioUrl = payload.getMetadata().get("audioUrl");
            if (audioUrl != null && !audioUrl.isEmpty()) {
                request.setAudioUrl(audioUrl);
            }
        } else if (payload.getMetadata() != null && payload.getMetadata().containsKey("location")) {
            // Location message
            WasenderMessageRequest.Location location = new WasenderMessageRequest.Location();
            if (payload.getMetadata().containsKey("latitude")) {
                try {
                    location.setLatitude(Double.parseDouble(payload.getMetadata().get("latitude")));
                } catch (NumberFormatException e) {
                    log.warn("Invalid latitude value: {}", payload.getMetadata().get("latitude"));
                }
            }
            if (payload.getMetadata().containsKey("longitude")) {
                try {
                    location.setLongitude(Double.parseDouble(payload.getMetadata().get("longitude")));
                } catch (NumberFormatException e) {
                    log.warn("Invalid longitude value: {}", payload.getMetadata().get("longitude"));
                }
            }
            if (payload.getMetadata().containsKey("locationName")) {
                location.setName(payload.getMetadata().get("locationName"));
            }
            if (payload.getMetadata().containsKey("locationAddress")) {
                location.setAddress(payload.getMetadata().get("locationAddress"));
            }
            request.setLocation(location);
        } else {
            // Plain text message
            request.setText(payload.getBody());
        }
        
        return request;
    }
    
    /**
     * Cleans recipient phone number or email by removing control characters and trimming.
     * This handles cases where line endings (\r\n or \r) or other control characters
     * might have been included in the recipient field.
     */
    private String cleanRecipient(String recipient) {
        if (recipient == null) {
            return null;
        }
        return recipient
            .replaceAll("\\r\\n", "")  // Remove Windows line endings
            .replaceAll("\\r", "")     // Remove carriage returns
            .replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "")  // Remove all control characters
            .trim();                    // Remove leading/trailing whitespace
    }
}

