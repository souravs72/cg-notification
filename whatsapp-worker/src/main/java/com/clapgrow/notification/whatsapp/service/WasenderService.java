package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.whatsapp.model.NotificationPayload;
import com.clapgrow.notification.whatsapp.model.WasenderMessageRequest;
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
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    public boolean sendMessage(NotificationPayload payload) {
        try {
            // Get API key from payload, fallback to environment variable for backward compatibility
            String apiKey = payload.getWasenderApiKey();
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is not provided in the payload. Please configure it first.");
            }
            
            WasenderMessageRequest request = buildWasenderRequest(payload);
            
            String response = webClient.post()
                .uri(wasenderBaseUrl + "/send-message")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            log.info("WhatsApp message sent successfully to {}: {}", payload.getRecipient(), response);
            return true;
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests e) {
            log.warn("Rate limit exceeded (429) for WhatsApp message to {}. Retry after delay.", 
                payload.getRecipient());
            // Return false to trigger retry with backoff
            return false;
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.error("HTTP error {} sending WhatsApp message to {}: {}", 
                e.getStatusCode(), payload.getRecipient(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("Error sending WhatsApp message via WASender to {}", payload.getRecipient(), e);
            return false;
        }
    }

    private WasenderMessageRequest buildWasenderRequest(NotificationPayload payload) {
        WasenderMessageRequest request = new WasenderMessageRequest();
        request.setTo(payload.getRecipient());
        
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
}

