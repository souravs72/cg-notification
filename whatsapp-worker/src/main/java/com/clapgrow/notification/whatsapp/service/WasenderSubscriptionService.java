package com.clapgrow.notification.whatsapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * WASender subscription service.
 * 
 * ⚠️ ARCHITECTURE: This service belongs in whatsapp-worker as it contains
 * provider-specific logic. It directly calls WASender API to fetch subscription
 * information (account limits, plan type, status).
 * 
 * This service handles:
 * - Fetching subscription/account info from WASender API
 * - Parsing subscription data (plan type, status, session limits)
 * - Error categorization for retry logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderSubscriptionService {
    
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    /**
     * Get subscription information from WASender API.
     * Uses GET /api/user endpoint to check subscription status.
     * 
     * @param apiKey WASender API key
     * @return Map containing subscription info with keys:
     *         - subscriptionType: "FREE_TRIAL" or "PAID"
     *         - subscriptionStatus: "ACTIVE", "EXPIRED", etc.
     *         - sessionsAllowed: Integer or null (if not provided by API)
     * @throws RuntimeException with error category in message for error handling
     */
    public Map<String, Object> getSubscriptionInfo(String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            String userUrl = wasenderBaseUrl + "/user";
            
            log.info("Fetching user/subscription info from WASender API");
            
            String response = webClient.get()
                .uri(userUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                log.warn("Empty response from WASender API user endpoint");
                throw new RuntimeException("TEMPORARY:Empty response from WASender API");
            }
            
            // Parse response
            JsonNode jsonNode;
            try {
                jsonNode = objectMapper.readTree(response);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse WASender API response", e);
                throw new RuntimeException("TEMPORARY:Invalid JSON response from WASender API", e);
            }
            
            Map<String, Object> info = new HashMap<>();
            
            // Extract subscription information from response
            if (jsonNode.has("data")) {
                JsonNode data = jsonNode.get("data");
                
                // Check for subscription/plan information
                if (data.has("subscription")) {
                    JsonNode subscription = data.get("subscription");
                    String plan = subscription.has("plan") ? subscription.get("plan").asText() : null;
                    String status = subscription.has("status") ? subscription.get("status").asText() : null;
                    
                    if (plan != null) {
                        info.put("subscriptionType", plan.toLowerCase().contains("trial") || plan.toLowerCase().contains("free") 
                            ? "FREE_TRIAL" : "PAID");
                    } else {
                        info.put("subscriptionType", "FREE_TRIAL");
                    }
                    
                    if (status != null) {
                        info.put("subscriptionStatus", status.toUpperCase());
                    } else {
                        info.put("subscriptionStatus", "ACTIVE");
                    }
                } else {
                    // Default to free trial if no subscription info
                    info.put("subscriptionType", "FREE_TRIAL");
                    info.put("subscriptionStatus", "ACTIVE");
                }
                
                // Get sessions limit
                if (data.has("sessions_limit") || data.has("sessionsAllowed")) {
                    int limit = data.has("sessions_limit") 
                        ? data.get("sessions_limit").asInt() 
                        : data.get("sessionsAllowed").asInt();
                    info.put("sessionsAllowed", limit);
                } else {
                    // WASender does not reliably expose session limits in API response
                    info.put("sessionsAllowed", null);
                }
            } else {
                // If response doesn't have expected structure, check root level
                if (jsonNode.has("subscription")) {
                    JsonNode subscription = jsonNode.get("subscription");
                    String plan = subscription.has("plan") ? subscription.get("plan").asText() : null;
                    info.put("subscriptionType", plan != null && (plan.toLowerCase().contains("trial") || plan.toLowerCase().contains("free"))
                        ? "FREE_TRIAL" : "PAID");
                    info.put("subscriptionStatus", subscription.has("status") 
                        ? subscription.get("status").asText().toUpperCase() : "ACTIVE");
                    if (subscription.has("sessions_limit")) {
                        info.put("sessionsAllowed", subscription.get("sessions_limit").asInt());
                    } else {
                        info.put("sessionsAllowed", null);
                    }
                } else {
                    // Unexpected response structure
                    throw new RuntimeException("TEMPORARY:Unexpected response structure from WASender API - missing subscription data");
                }
            }
            
            log.info("Subscription info: type={}, status={}, sessionsAllowed={}", 
                info.get("subscriptionType"), info.get("subscriptionStatus"), info.get("sessionsAllowed"));
            
            return info;
            
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("Invalid WASender API key - authentication failed");
            throw new RuntimeException("AUTH:Invalid WASender API key - authentication failed", e);
        } catch (WebClientResponseException.Forbidden e) {
            log.error("WASender API key access forbidden - auth revoked");
            throw new RuntimeException("AUTH:WASender API key access forbidden - auth revoked", e);
        } catch (WebClientResponseException e) {
            // Check for rate limiting (429) or server errors (5xx) - these are temporary
            if (e.getStatusCode().value() == 429 || e.getStatusCode().is5xxServerError()) {
                log.warn("Temporary error fetching subscription info from WASender API: Status={}", 
                    e.getStatusCode());
                throw new RuntimeException("TEMPORARY:Temporary error from WASender API: " + e.getStatusCode(), e);
            } else {
                // SECURITY: Do not log raw response body - may contain secrets
                log.error("Error fetching subscription info from WASender API: Status={}, response redacted", 
                    e.getStatusCode());
                throw new RuntimeException("PERMANENT:Error from WASender API: " + e.getStatusCode(), e);
            }
        } catch (Exception e) {
            // Check if this is a timeout exception
            Throwable rootCause = Exceptions.unwrap(e);
            if (rootCause instanceof TimeoutException || 
                e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout")) {
                log.warn("WASender API timeout - request took longer than 30 seconds");
                throw new RuntimeException("TEMPORARY:WASender API timeout", e);
            }
            
            // Check if error message contains category prefix
            String message = e.getMessage();
            if (message != null && (message.startsWith("TEMPORARY:") || message.startsWith("AUTH:") || message.startsWith("PERMANENT:"))) {
                throw e; // Re-throw if already categorized
            }
            
            // For all other exceptions, treat as temporary
            log.error("Unexpected error fetching subscription info from WASender API", e);
            throw new RuntimeException("TEMPORARY:Unexpected error fetching subscription info: " + message, e);
        }
    }
}

