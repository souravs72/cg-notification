package com.clapgrow.notification.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Client implementation of MessagingSubscriptionService that calls whatsapp-worker.
 * 
 * ⚠️ ARCHITECTURE: This client calls whatsapp-worker's REST API instead of
 * directly calling WASender API. This maintains proper module boundaries:
 * - notification-api: API gateway, no provider logic
 * - whatsapp-worker: Provider control-plane logic
 * 
 * ⚠️ BLOCKING OPERATIONS: This client uses WebClient.block() which blocks
 * the calling thread. This is ACCEPTABLE because:
 * - Used for user registration and subscription refresh (low-frequency operations)
 * - Not on hot request paths (not called during message sending)
 * - Spring MVC (servlet-based) controllers require synchronous responses
 * 
 * This adapter converts worker API responses to MessagingSubscriptionService.SubscriptionInfo
 * format expected by UserService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderSubscriptionServiceClient implements MessagingSubscriptionService {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${whatsapp-worker.api.base-url:http://whatsapp-worker:8082}")
    private String whatsappWorkerBaseUrl;
    
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF = 
        new ParameterizedTypeReference<Map<String, Object>>() {};

    @Override
    public String getProviderName() {
        return "wasender"; // Lowercase for stable DB storage, metrics, config
    }

    @Override
    public SubscriptionInfo getSubscriptionInfo(String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            Map<String, Object> response = webClient.get()
                .uri("/api/whatsapp/sessions/subscription")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
            
            if (response == null) {
                throw new SubscriptionValidationException(
                    "Empty response from whatsapp-worker",
                    SubscriptionValidationException.ErrorCategory.TEMPORARY
                );
            }
            
            // Check for error response
            if (response.containsKey("error")) {
                String errorMessage = (String) response.get("error");
                String categoryStr = (String) response.getOrDefault("category", "TEMPORARY");
                SubscriptionValidationException.ErrorCategory category = parseErrorCategory(categoryStr);
                throw new SubscriptionValidationException(errorMessage, category);
            }
            
            // Convert worker response to SubscriptionInfo
            SubscriptionInfo info = new SubscriptionInfo();
            info.setSubscriptionType((String) response.get("subscriptionType"));
            info.setSubscriptionStatus((String) response.get("subscriptionStatus"));
            Object sessionsAllowed = response.get("sessionsAllowed");
            if (sessionsAllowed != null) {
                if (sessionsAllowed instanceof Integer) {
                    info.setSessionsAllowed((Integer) sessionsAllowed);
                } else if (sessionsAllowed instanceof Number) {
                    info.setSessionsAllowed(((Number) sessionsAllowed).intValue());
                }
            }
            
            return info;
            
        } catch (WebClientResponseException.Unauthorized e) {
            throw new SubscriptionValidationException(
                "Invalid WASender API key - authentication failed",
                SubscriptionValidationException.ErrorCategory.AUTH,
                e
            );
        } catch (WebClientResponseException.Forbidden e) {
            throw new SubscriptionValidationException(
                "WASender API key access forbidden - auth revoked",
                SubscriptionValidationException.ErrorCategory.AUTH,
                e
            );
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError()) {
                throw new SubscriptionValidationException(
                    "Temporary error from whatsapp-worker: " + e.getStatusCode(),
                    SubscriptionValidationException.ErrorCategory.TEMPORARY,
                    e
                );
            } else {
                throw new SubscriptionValidationException(
                    "Error from whatsapp-worker: " + e.getStatusCode(),
                    SubscriptionValidationException.ErrorCategory.PERMANENT,
                    e
                );
            }
        } catch (SubscriptionValidationException e) {
            // Re-throw if already wrapped
            throw e;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.toLowerCase().contains("timeout")) {
                throw new SubscriptionValidationException(
                    "whatsapp-worker API timeout",
                    SubscriptionValidationException.ErrorCategory.TEMPORARY,
                    e
                );
            }
            throw new SubscriptionValidationException(
                "Unexpected error fetching subscription info: " + message,
                SubscriptionValidationException.ErrorCategory.TEMPORARY,
                e
            );
        }
    }
    
    private SubscriptionValidationException.ErrorCategory parseErrorCategory(String categoryStr) {
        if (categoryStr == null) {
            return SubscriptionValidationException.ErrorCategory.TEMPORARY;
        }
        try {
            return SubscriptionValidationException.ErrorCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SubscriptionValidationException.ErrorCategory.TEMPORARY;
        }
    }
}


