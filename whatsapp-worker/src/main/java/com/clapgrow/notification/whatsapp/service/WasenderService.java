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
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;
    
    @Value("${wasender.api.key:}")
    private String wasenderApiKey;

    public boolean sendMessage(NotificationPayload payload) {
        try {
            WasenderMessageRequest request = buildWasenderRequest(payload);
            
            String response = webClient.post()
                .uri(wasenderBaseUrl + "/send-message")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + wasenderApiKey)
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
        
        // Determine message type based on available fields
        if (payload.getImageUrl() != null && !payload.getImageUrl().isEmpty()) {
            request.setImageUrl(payload.getImageUrl());
            request.setCaption(payload.getCaption() != null ? payload.getCaption() : payload.getBody());
        } else if (payload.getVideoUrl() != null && !payload.getVideoUrl().isEmpty()) {
            request.setVideoUrl(payload.getVideoUrl());
            request.setCaption(payload.getCaption() != null ? payload.getCaption() : payload.getBody());
        } else if (payload.getDocumentUrl() != null && !payload.getDocumentUrl().isEmpty()) {
            request.setDocumentUrl(payload.getDocumentUrl());
            request.setFileName(payload.getFileName());
            request.setCaption(payload.getCaption());
        } else {
            // Plain text message
            request.setText(payload.getBody());
        }
        
        return request;
    }
}

