package com.clapgrow.notification.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderSubscriptionService {
    
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    @Data
    public static class SubscriptionInfo {
        private String subscriptionType; // FREE_TRIAL, PAID
        private String subscriptionStatus; // ACTIVE, EXPIRED, CANCELLED
        private Integer sessionsAllowed;
    }

    /**
     * Get subscription information from WASender API
     * Uses GET /api/user endpoint to check subscription status
     */
    public SubscriptionInfo getSubscriptionInfo(String apiKey) {
        try {
            WebClient webClient = webClientBuilder.build();
            
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
                return getDefaultSubscriptionInfo();
            }
            
            // Parse response
            JsonNode jsonNode = objectMapper.readTree(response);
            SubscriptionInfo info = new SubscriptionInfo();
            
            // Extract subscription information from response
            // Adjust these paths based on actual WASender API response structure
            if (jsonNode.has("data")) {
                JsonNode data = jsonNode.get("data");
                
                // Check for subscription/plan information
                if (data.has("subscription")) {
                    JsonNode subscription = data.get("subscription");
                    String plan = subscription.has("plan") ? subscription.get("plan").asText() : null;
                    String status = subscription.has("status") ? subscription.get("status").asText() : null;
                    
                    if (plan != null) {
                        info.setSubscriptionType(plan.toLowerCase().contains("trial") || plan.toLowerCase().contains("free") 
                            ? "FREE_TRIAL" : "PAID");
                    } else {
                        info.setSubscriptionType("FREE_TRIAL");
                    }
                    
                    if (status != null) {
                        info.setSubscriptionStatus(status.toUpperCase());
                    } else {
                        info.setSubscriptionStatus("ACTIVE");
                    }
                } else {
                    // Default to free trial if no subscription info
                    info.setSubscriptionType("FREE_TRIAL");
                    info.setSubscriptionStatus("ACTIVE");
                }
                
                // Get sessions limit
                if (data.has("sessions_limit") || data.has("sessionsAllowed")) {
                    int limit = data.has("sessions_limit") 
                        ? data.get("sessions_limit").asInt() 
                        : data.get("sessionsAllowed").asInt();
                    info.setSessionsAllowed(limit);
                } else {
                    // Default limits based on subscription type
                    info.setSessionsAllowed("FREE_TRIAL".equals(info.getSubscriptionType()) ? 10 : 100);
                }
            } else {
                // If response doesn't have expected structure, check root level
                if (jsonNode.has("subscription")) {
                    JsonNode subscription = jsonNode.get("subscription");
                    String plan = subscription.has("plan") ? subscription.get("plan").asText() : null;
                    info.setSubscriptionType(plan != null && (plan.toLowerCase().contains("trial") || plan.toLowerCase().contains("free"))
                        ? "FREE_TRIAL" : "PAID");
                    info.setSubscriptionStatus(subscription.has("status") 
                        ? subscription.get("status").asText().toUpperCase() : "ACTIVE");
                    info.setSessionsAllowed(subscription.has("sessions_limit") 
                        ? subscription.get("sessions_limit").asInt() 
                        : ("FREE_TRIAL".equals(info.getSubscriptionType()) ? 10 : 100));
                } else {
                    return getDefaultSubscriptionInfo();
                }
            }
            
            log.info("Subscription info: type={}, status={}, sessionsAllowed={}", 
                info.getSubscriptionType(), info.getSubscriptionStatus(), info.getSessionsAllowed());
            
            return info;
            
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("Invalid WASender API key");
            throw new IllegalArgumentException("Invalid WASender API key");
        } catch (WebClientResponseException e) {
            log.error("Error fetching subscription info from WASender API: Status={}, Response={}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            // Return default info on error
            return getDefaultSubscriptionInfo();
        } catch (Exception e) {
            log.error("Error fetching subscription info from WASender API", e);
            // Return default info on error
            return getDefaultSubscriptionInfo();
        }
    }

    private SubscriptionInfo getDefaultSubscriptionInfo() {
        SubscriptionInfo info = new SubscriptionInfo();
        info.setSubscriptionType("FREE_TRIAL");
        info.setSubscriptionStatus("ACTIVE");
        info.setSessionsAllowed(10);
        return info;
    }
}

