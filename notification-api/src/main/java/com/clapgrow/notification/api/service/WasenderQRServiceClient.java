package com.clapgrow.notification.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * REST client for whatsapp-worker session management API.
 * 
 * ⚠️ ARCHITECTURE: This service calls whatsapp-worker's REST API instead of
 * directly calling WASender API. This maintains proper module boundaries:
 * - notification-api: API gateway, no provider logic
 * - whatsapp-worker: Provider control-plane logic
 * 
 * ⚠️ BLOCKING OPERATIONS: This client uses WebClient.block() which blocks
 * the calling thread. This is ACCEPTABLE because:
 * - All endpoints using this client are ADMIN-ONLY (/admin/*)
 * - Admin operations are low-frequency, not on hot request paths
 * - Spring MVC (servlet-based) controllers require synchronous responses
 * 
 * ⚠️ DO NOT USE ON HOT PATHS: Never call this from:
 * - Message sending endpoints (use database-stored session info instead)
 * - High-frequency API endpoints
 * - Background job processing
 * 
 * If you need async provider operations, use Kafka messages instead.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderQRServiceClient {
    
    private final WebClient.Builder webClientBuilder;
    
    @Value("${whatsapp-worker.api.base-url:http://whatsapp-worker:8082}")
    private String whatsappWorkerBaseUrl;
    
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF = 
        new ParameterizedTypeReference<Map<String, Object>>() {};

    /**
     * Get QR code for a WhatsApp session.
     */
    public Map<String, Object> getQRCode(String sessionIdentifier, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/whatsapp/sessions/qrcode");
                    // Try to determine if it's a sessionId (numeric) or sessionName
                    if (sessionIdentifier != null && sessionIdentifier.matches("^\\d+$")) {
                        builder.queryParam("sessionId", sessionIdentifier);
                    } else {
                        builder.queryParam("sessionName", sessionIdentifier);
                    }
                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API for QR code: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to get QR code: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API for QR code", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Connect a WhatsApp session.
     */
    public Map<String, Object> connectSession(String sessionName, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.post()
                .uri("/api/whatsapp/sessions/{sessionName}/connect", sessionName)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API to connect session: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to connect session: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API to connect session", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Create a WhatsApp session.
     */
    public Map<String, Object> createSession(
            String sessionName, String phoneNumber, Boolean accountProtection, Boolean logMessages,
            String webhookUrl, Boolean webhookEnabled, String[] webhookEvents,
            Boolean readIncomingMessages, Boolean autoRejectCalls,
            Boolean ignoreGroups, Boolean ignoreChannels, Boolean ignoreBroadcasts,
            String apiKey) {
        try {
            java.util.Map<String, Object> requestBody = new java.util.HashMap<>();
            requestBody.put("sessionName", sessionName);
            requestBody.put("phoneNumber", phoneNumber);
            requestBody.put("accountProtection", accountProtection != null ? accountProtection : true);
            requestBody.put("logMessages", logMessages != null ? logMessages : true);
            if (webhookUrl != null) requestBody.put("webhookUrl", webhookUrl);
            if (webhookEnabled != null) requestBody.put("webhookEnabled", webhookEnabled);
            if (webhookEvents != null) requestBody.put("webhookEvents", webhookEvents);
            if (readIncomingMessages != null) requestBody.put("readIncomingMessages", readIncomingMessages);
            if (autoRejectCalls != null) requestBody.put("autoRejectCalls", autoRejectCalls);
            if (ignoreGroups != null) requestBody.put("ignoreGroups", ignoreGroups);
            if (ignoreChannels != null) requestBody.put("ignoreChannels", ignoreChannels);
            if (ignoreBroadcasts != null) requestBody.put("ignoreBroadcasts", ignoreBroadcasts);
            
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.post()
                .uri("/api/whatsapp/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API to create session: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to create session: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API to create session", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Get message logs for a WhatsApp session.
     */
    public Map<String, Object> getMessageLogs(String sessionIdentifier, Integer page, Integer perPage, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/whatsapp/sessions/message-logs");
                    // Try to determine if it's a sessionId (numeric) or sessionName
                    if (sessionIdentifier != null && sessionIdentifier.matches("^\\d+$")) {
                        builder.queryParam("sessionId", sessionIdentifier);
                    } else {
                        builder.queryParam("sessionName", sessionIdentifier);
                    }
                    return builder
                        .queryParam("page", page != null ? page : 1)
                        .queryParam("per_page", perPage != null ? perPage : 10)
                        .build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API for message logs: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to get message logs: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API for message logs", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Get session logs for a WhatsApp session.
     */
    public Map<String, Object> getSessionLogs(String sessionIdentifier, Integer page, Integer perPage, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/whatsapp/sessions/session-logs");
                    // Try to determine if it's a sessionId (numeric) or sessionName
                    if (sessionIdentifier != null && sessionIdentifier.matches("^\\d+$")) {
                        builder.queryParam("sessionId", sessionIdentifier);
                    } else {
                        builder.queryParam("sessionName", sessionIdentifier);
                    }
                    return builder
                        .queryParam("page", page != null ? page : 1)
                        .queryParam("per_page", perPage != null ? perPage : 10)
                        .build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API for session logs: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to get session logs: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API for session logs", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Get all WhatsApp sessions.
     */
    public Map<String, Object> getAllSessions(String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.get()
                .uri("/api/whatsapp/sessions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API for all sessions: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to get sessions: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API for all sessions", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Get session details by identifier.
     */
    public Map<String, Object> getSessionDetails(String sessionIdentifier, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.get()
                .uri("/api/whatsapp/sessions/{sessionIdentifier}", sessionIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API for session details: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to get session details: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API for session details", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Delete a WhatsApp session.
     */
    public Map<String, Object> deleteSession(String sessionIdentifier, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.delete()
                .uri("/api/whatsapp/sessions/{sessionIdentifier}", sessionIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API to delete session: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to delete session: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API to delete session", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Update a WhatsApp session.
     */
    public Map<String, Object> updateSession(String sessionIdentifier, String apiKey, Map<String, Object> updateData) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.put()
                .uri("/api/whatsapp/sessions/{sessionIdentifier}", sessionIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateData)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API to update session: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to update session: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API to update session", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Disconnect a WhatsApp session.
     */
    public Map<String, Object> disconnectSession(String sessionIdentifier, String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.post()
                .uri("/api/whatsapp/sessions/{sessionIdentifier}/disconnect", sessionIdentifier)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API to disconnect session: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to disconnect session: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API to disconnect session", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }

    /**
     * Get WhatsApp session status.
     */
    public Map<String, Object> getSessionStatus(String apiKey) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(whatsappWorkerBaseUrl).build();
            return webClient.get()
                .uri("/api/whatsapp/sessions/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling whatsapp-worker API for session status: Status={}", e.getStatusCode());
            return Map.of(
                "success", false,
                "error", "Failed to get session status: " + e.getMessage(),
                "statusCode", e.getStatusCode().value()
            );
        } catch (Exception e) {
            log.error("Unexpected error calling whatsapp-worker API for session status", e);
            return Map.of(
                "success", false,
                "error", "Unexpected error: " + e.getMessage()
            );
        }
    }
}

