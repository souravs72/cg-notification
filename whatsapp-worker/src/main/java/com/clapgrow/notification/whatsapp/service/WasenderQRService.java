package com.clapgrow.notification.whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import reactor.util.retry.Retry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WASender QR code and session management service.
 * 
 * ⚠️ ARCHITECTURE: This service belongs in whatsapp-worker as it contains
 * provider-specific control-plane logic. It directly calls WASender API.
 * 
 * This service handles:
 * - QR code generation and management
 * - Session lifecycle (create, connect, disconnect, delete, update)
 * - Session status and details retrieval
 * - Message and session logs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderQRService {
    
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    /**
     * Check if a session identifier is numeric (session ID) or a name.
     * @param identifier Session identifier to check
     * @return true if identifier is numeric (all digits), false otherwise
     */
    boolean isNumericSessionId(String identifier) {
        return identifier != null && identifier.trim().matches("^\\d+$");
    }

    /**
     * Determine if the /connect endpoint should be used based on error response.
     * @param statusCode HTTP status code from the response
     * @param errorBody Error response body (may be null)
     * @return true if /connect endpoint should be used, false otherwise
     */
    boolean shouldUseConnectEndpoint(int statusCode, String errorBody) {
        // 404 means session doesn't exist - use /connect to initialize
        if (statusCode == 404) {
            return true;
        }
        
        // NEED_SCAN error means session needs to be initialized
        if (errorBody != null && errorBody.contains("NEED_SCAN")) {
            return true;
        }
        
        // "No query results" or "not found" errors indicate session doesn't exist
        if (errorBody != null && (errorBody.contains("No query results") || errorBody.contains("not found"))) {
            return true;
        }
        
        return false;
    }

    /**
     * Check if error indicates session is already connected.
     * @param errorBody Error response body (may be null)
     * @return true if session is already connected, false otherwise
     */
    boolean isSessionAlreadyConnected(String errorBody) {
        return errorBody != null && 
               (errorBody.contains("already connected") || errorBody.contains("is already connected"));
    }

    /**
     * Build QR code URL for a session.
     * @param sessionId Session ID
     * @return Full URL for QR code endpoint
     */
    String buildQRCodeUrl(String sessionId) {
        return wasenderBaseUrl + "/whatsapp-sessions/" + sessionId + "/qrcode";
    }

    /**
     * Build connect URL for a session.
     * @param sessionId Session ID (must be numeric)
     * @return Full URL for connect endpoint
     */
    String buildConnectUrl(String sessionId) {
        return wasenderBaseUrl + "/whatsapp-sessions/" + sessionId + "/connect";
    }

    /**
     * Resolve session name to session ID.
     * This method ALWAYS resolves names to IDs - never returns a name.
     * 
     * @param sessionName Session name to resolve
     * @param apiKey WASender API key
     * @return Session ID (numeric string)
     * @throws IllegalArgumentException if session name cannot be resolved
     */
    private String resolveSessionIdByName(String sessionName, String apiKey) {
        if (sessionName == null || sessionName.trim().isEmpty()) {
            throw new IllegalArgumentException("Session name cannot be null or empty");
        }
        
        log.info("Resolving session name '{}' to session ID", sessionName);
        
        try {
            Map<String, Object> allSessions = getAllSessions(apiKey);
            if (allSessions != null && allSessions.containsKey("data")) {
                Object dataObj = allSessions.get("data");
                if (dataObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> sessions = (List<Map<String, Object>>) dataObj;
                    for (Map<String, Object> session : sessions) {
                        Object sessionNameObj = session.get("name");
                        if (sessionNameObj != null && sessionNameObj.toString().equals(sessionName.trim())) {
                            Object idObj = session.get("id");
                            if (idObj != null) {
                                String sessionId = idObj.toString();
                                log.info("Resolved session name '{}' to session ID: {}", sessionName, sessionId);
                                return sessionId;
                            }
                        }
                    }
                }
            }
            
            throw new IllegalArgumentException("Session not found with name: " + sessionName);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            log.error("Failed to resolve session name '{}' to session ID", sessionName, e);
            throw new IllegalArgumentException("Failed to resolve session name '" + sessionName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Ensure session identifier is a numeric ID.
     * If it's a name, resolve it to an ID first.
     * 
     * @param sessionIdentifier Session ID or name
     * @param apiKey WASender API key (required if identifier is a name)
     * @return Numeric session ID
     * @throws IllegalArgumentException if name cannot be resolved
     */
    private String ensureNumericSessionId(String sessionIdentifier, String apiKey) {
        if (sessionIdentifier == null || sessionIdentifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Session identifier cannot be null or empty");
        }
        
        if (isNumericSessionId(sessionIdentifier)) {
            return sessionIdentifier.trim();
        }
        
        // It's a name - resolve it to an ID
        return resolveSessionIdByName(sessionIdentifier.trim(), apiKey);
    }

    /**
     * Get QR code for a WhatsApp session.
     * @param sessionIdentifier Can be either session ID (integer as string) or session name.
     *                          If it's a name, we'll need to look up the session first to get its ID.
     * @param apiKey The WASender API key to use
     */
    public Map<String, Object> getQRCode(String sessionIdentifier, String apiKey) {
        try {
            // Validate input
            if (sessionIdentifier == null || sessionIdentifier.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Session identifier cannot be null or empty");
                return errorResponse;
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "WASender API key is required");
                return errorResponse;
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            // WASender requires numeric session ID in URLs - never use names directly
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            // First, try to get QR code using /qrcode endpoint (for refreshing existing QR codes)
            // WASender API requires the session ID (integer) in the URL
            String qrCodeUrl = buildQRCodeUrl(sessionId);
            String qrCodeResponse = null;
            boolean useConnectEndpoint = false;
            
            try {
                qrCodeResponse = webClient.get()
                    .uri(qrCodeUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(2))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException 
                            && ((WebClientResponseException) throwable).getStatusCode().is4xxClientError())))
                    .block();
            } catch (WebClientResponseException e) {
                String errorBody = e.getResponseBodyAsString();
                int statusCode = e.getStatusCode().value();
                
                // Check if session is already connected
                if (isSessionAlreadyConnected(errorBody)) {
                    log.info("Session {} is already connected, cannot generate QR code", sessionId);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("error", "WhatsApp session is already connected");
                    errorResponse.put("status", "connected");
                    errorResponse.put("statusCode", statusCode);
                    errorResponse.put("statusText", e.getStatusCode().toString());
                    errorResponse.put("sessionId", sessionId);
                    return errorResponse;
                }
                
                // Check if we should use /connect endpoint
                if (shouldUseConnectEndpoint(statusCode, errorBody)) {
                    log.info("Using /connect endpoint to initialize session: {} (status: {}, error: {})", 
                        sessionId, statusCode, errorBody != null ? "present" : "none");
                    useConnectEndpoint = true;
                } else {
                    // Re-throw if it's a different error
                    throw e;
                }
            }
            
            // If /qrcode failed because session needs to be initialized, use /connect endpoint
            if (useConnectEndpoint || (qrCodeResponse == null)) {
                log.info("Calling /connect endpoint to initialize session and get QR code: {}", sessionId);
                // WASender API requires session ID (integer) in the URL
                String connectUrl = buildConnectUrl(sessionId);
                qrCodeResponse = webClient.post()
                    .uri(connectUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(2))
                        .filter(throwable -> !(throwable instanceof WebClientResponseException 
                            && ((WebClientResponseException) throwable).getStatusCode().is4xxClientError())))
                    .block();
            }
            
            // Check if response is null or empty
            if (qrCodeResponse == null || qrCodeResponse.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response received from WASender API");
                return errorResponse;
            }
            
            // Parse QR code from response
            // /connect response format: {"success": true, "data": {"status": "NEED_SCAN", "qrCode": "..."}}
            // /qrcode response format: {"success": true, "data": {"qrCode": "..."}}
            String qrCodeData = null;
            String status = null;
            String actualSessionId = sessionId; // May be updated from response
            try {
                JsonNode jsonNode = objectMapper.readTree(qrCodeResponse);
                if (jsonNode.has("data")) {
                    JsonNode dataNode = jsonNode.get("data");
                    if (dataNode.has("qrCode")) {
                        qrCodeData = dataNode.get("qrCode").asText();
                    }
                    if (dataNode.has("status")) {
                        status = dataNode.get("status").asText();
                    }
                    // Extract session ID from response if available (from /connect endpoint)
                    if (dataNode.has("id")) {
                        actualSessionId = dataNode.get("id").asText();
                        log.info("Extracted session ID from response: {}", actualSessionId);
                    }
                } else if (jsonNode.has("qrCode")) {
                    // Fallback: check root level
                    qrCodeData = jsonNode.get("qrCode").asText();
                }
                // Also check root level for session ID
                if (jsonNode.has("id") && actualSessionId == null) {
                    actualSessionId = jsonNode.get("id").asText();
                }
            } catch (Exception e) {
                log.error("Error parsing QR code response: {}", e.getMessage());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Invalid response format from WASender API: " + qrCodeResponse);
                return errorResponse;
            }
            
            if (qrCodeData == null || qrCodeData.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "QR code not found in response from WASender API");
                if (status != null) {
                    errorResponse.put("status", status);
                }
                return errorResponse;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("qrCode", qrCodeData);
            // Always include session ID - use the one from response if available, otherwise use the one we tried
            response.put("sessionId", actualSessionId);
            // Include original identifier if it was a name (for user reference)
            if (!isNumericSessionId(sessionIdentifier) && !actualSessionId.equals(sessionId)) {
                response.put("sessionName", sessionIdentifier);
            }
            if (status != null) {
                response.put("status", status);
            }
            
            return response;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            // SECURITY: Do not log raw response body - may contain secrets
            log.error("WASender API error fetching QR code for session '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            // Use only parsed message/error fields; never expose raw response body
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            // Add status code for debugging
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error fetching QR code from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> createSession(String sessionName, String phoneNumber, Boolean accountProtection, Boolean logMessages,
                                             String webhookUrl, Boolean webhookEnabled, String[] webhookEvents,
                                             Boolean readIncomingMessages, Boolean autoRejectCalls,
                                             Boolean ignoreGroups, Boolean ignoreChannels, Boolean ignoreBroadcasts,
                                             String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            String createUrl = wasenderBaseUrl + "/whatsapp-sessions";
            
            Map<String, Object> requestBody = new HashMap<>();
            
            // Required fields
            requestBody.put("name", sessionName);
            requestBody.put("phone_number", phoneNumber); // Required - must be in international format
            requestBody.put("account_protection", accountProtection != null ? accountProtection : true);
            requestBody.put("log_messages", logMessages != null ? logMessages : true);
            
            // Optional fields - only include if provided
            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                requestBody.put("webhook_url", webhookUrl);
            }
            if (webhookEnabled != null) {
                requestBody.put("webhook_enabled", webhookEnabled);
            }
            if (webhookEvents != null && webhookEvents.length > 0) {
                requestBody.put("webhook_events", webhookEvents);
            }
            if (readIncomingMessages != null) {
                requestBody.put("read_incoming_messages", readIncomingMessages);
            }
            if (autoRejectCalls != null) {
                requestBody.put("auto_reject_calls", autoRejectCalls);
            }
            if (ignoreGroups != null) {
                requestBody.put("ignore_groups", ignoreGroups);
            }
            if (ignoreChannels != null) {
                requestBody.put("ignore_channels", ignoreChannels);
            }
            if (ignoreBroadcasts != null) {
                requestBody.put("ignore_broadcasts", ignoreBroadcasts);
            }
            
            log.info("Creating WASender session: {} with API key configured", sessionName);
            
            String response = webClient.post()
                .uri(createUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            // Check if response is null or empty
            if (response == null || response.trim().isEmpty()) {
                log.warn("Empty response received from WASender API when creating session: {}", sessionName);
            }
            
            // Parse response to extract session ID, name, and API key
            String sessionId = null;
            String actualSessionName = sessionName;
            String sessionApiKey = null;
            try {
                if (response != null && !response.trim().isEmpty()) {
                    JsonNode responseJson = objectMapper.readTree(response);
                    
                    // Try multiple possible response structures
                    if (responseJson.has("data")) {
                        JsonNode dataNode = responseJson.get("data");
                        if (dataNode.isObject()) {
                            if (dataNode.has("id")) {
                                sessionId = dataNode.get("id").asText();
                                log.info("Extracted session ID from data.id: {}", sessionId);
                            }
                            if (dataNode.has("name")) {
                                actualSessionName = dataNode.get("name").asText();
                            }
                            // Extract API key from data object
                            if (dataNode.has("api_key")) {
                                sessionApiKey = dataNode.get("api_key").asText();
                                log.info("Extracted session API key from data.api_key");
                            } else if (dataNode.has("apiKey")) {
                                sessionApiKey = dataNode.get("apiKey").asText();
                                log.info("Extracted session API key from data.apiKey");
                            } else if (dataNode.has("token")) {
                                sessionApiKey = dataNode.get("token").asText();
                                log.info("Extracted session API key from data.token");
                            }
                        } else if (dataNode.isArray() && dataNode.size() > 0) {
                            // If data is an array, get the first element
                            JsonNode firstItem = dataNode.get(0);
                            if (firstItem.has("id")) {
                                sessionId = firstItem.get("id").asText();
                                log.info("Extracted session ID from data[0].id: {}", sessionId);
                            }
                            if (firstItem.has("name")) {
                                actualSessionName = firstItem.get("name").asText();
                            }
                            // Extract API key from first item
                            if (firstItem.has("api_key")) {
                                sessionApiKey = firstItem.get("api_key").asText();
                                log.info("Extracted session API key from data[0].api_key");
                            } else if (firstItem.has("apiKey")) {
                                sessionApiKey = firstItem.get("apiKey").asText();
                                log.info("Extracted session API key from data[0].apiKey");
                            } else if (firstItem.has("token")) {
                                sessionApiKey = firstItem.get("token").asText();
                                log.info("Extracted session API key from data[0].token");
                            }
                        }
                    }
                    
                    // Also check root level
                    if (sessionId == null && responseJson.has("id")) {
                        sessionId = responseJson.get("id").asText();
                        log.info("Extracted session ID from root id: {}", sessionId);
                    }
                    if (responseJson.has("name") && actualSessionName.equals(sessionName)) {
                        actualSessionName = responseJson.get("name").asText();
                    }
                    // Check root level for API key
                    if (sessionApiKey == null) {
                        if (responseJson.has("api_key")) {
                            sessionApiKey = responseJson.get("api_key").asText();
                            log.info("Extracted session API key from root api_key");
                        } else if (responseJson.has("apiKey")) {
                            sessionApiKey = responseJson.get("apiKey").asText();
                            log.info("Extracted session API key from root apiKey");
                        } else if (responseJson.has("token")) {
                            sessionApiKey = responseJson.get("token").asText();
                            log.info("Extracted session API key from root token");
                        }
                    }
                    
                    log.info("Session creation response parsed - ID: {}, Name: {}, Has API Key: {}", 
                            sessionId, actualSessionName, sessionApiKey != null);
                } else {
                    log.warn("Empty response from WASender API when creating session");
                }
            } catch (Exception e) {
                log.error("Could not parse session creation response: {}", e.getMessage(), e);
                log.debug("Response was: {}", response);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", response != null ? response : "");
            result.put("sessionName", actualSessionName);
            // Include session ID if available - frontend can use this for QR code requests
            if (sessionId != null) {
                result.put("sessionId", sessionId);
                log.info("Session created successfully. ID: {}, Name: {}", sessionId, actualSessionName);
            } else {
                log.warn("Session created but ID not found in response. Name: {}", actualSessionName);
            }
            // Include session API key if found in response
            if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                result.put("sessionApiKey", sessionApiKey.trim());
                log.info("Session API key found in creation response for session: {} (ID: {})", actualSessionName, sessionId);
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            // SECURITY: Do not log raw response body - may contain secrets
            log.error("WASender API error creating session '{}': Status={}, response redacted", 
                sessionName, e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            // Use only parsed message/error fields; never expose raw response body
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        String message = errorJson.get("message").asText();
                        errorResponse.put("error", message);
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            // Add status code for debugging
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error creating WASender session", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    public Map<String, Object> connectSession(String sessionIdentifier, String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            String connectUrl = buildConnectUrl(sessionId);
            
            log.info("Connecting WASender session: {} (ID: {})", sessionIdentifier, sessionId);
            
            String response = webClient.post()
                .uri(connectUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(2))
                    .filter(throwable -> !(throwable instanceof WebClientResponseException 
                        && ((WebClientResponseException) throwable).getStatusCode().is4xxClientError())))
                .block();
            
            // Parse response to extract QR code and status
            // Response format: {"success": true, "data": {"status": "NEED_SCAN", "qrCode": "..."}}
            String qrCodeData = null;
            String status = null;
            if (response != null && !response.trim().isEmpty()) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(response);
                    if (jsonNode.has("data")) {
                        JsonNode dataNode = jsonNode.get("data");
                        if (dataNode.has("qrCode")) {
                            qrCodeData = dataNode.get("qrCode").asText();
                        }
                        if (dataNode.has("status")) {
                            status = dataNode.get("status").asText();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not parse connect response, returning raw response: {}", e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", response);
            result.put("sessionId", sessionId);
            // Include original identifier if it was a name (for user reference)
            if (!isNumericSessionId(sessionIdentifier)) {
                result.put("sessionName", sessionIdentifier);
            }
            if (qrCodeData != null) {
                result.put("qrCode", qrCodeData);
            }
            if (status != null) {
                result.put("status", status);
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            // SECURITY: Do not log raw response body - may contain secrets
            log.error("WASender API error connecting session '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error connecting WASender session", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
    
    /**
     * Get message logs for a WhatsApp session.
     * @param sessionIdentifier Session ID (integer as string) or session name
     * @param page Page number (default: 1)
     * @param perPage Items per page (default: 10)
     * @param apiKey The WASender API key to use
     * @return Map containing paginated message logs
     */
    public Map<String, Object> getMessageLogs(String sessionIdentifier, Integer page, Integer perPage, String apiKey) {
        try {
            // Validate input
            if (sessionIdentifier == null || sessionIdentifier.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Session identifier cannot be null or empty");
                return errorResponse;
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            // Build URL with pagination parameters - use message-logs endpoint
            String logsUrl = wasenderBaseUrl + "/whatsapp-sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8) + "/message-logs";
            if (page != null && page > 0) {
                logsUrl += "?page=" + page;
            } else {
                logsUrl += "?page=1";
            }
            if (perPage != null && perPage > 0) {
                logsUrl += "&per_page=" + perPage;
            } else {
                logsUrl += "&per_page=10";
            }
            
            log.info("Fetching message logs for session: {} (page: {}, per_page: {})", 
                sessionId, page != null ? page : 1, perPage != null ? perPage : 10);
            
            String response = webClient.get()
                .uri(logsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            // Parse response
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            }
            
            if (jsonNode.has("data")) {
                result.put("data", objectMapper.convertValue(jsonNode.get("data"), Map.class));
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error fetching message logs from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Get session logs for a WhatsApp session.
     * @param sessionIdentifier Session ID (integer as string) or session name
     * @param page Page number (default: 1)
     * @param perPage Items per page (default: 10)
     * @param apiKey The WASender API key to use
     * @return Map containing paginated session logs
     */
    public Map<String, Object> getSessionLogs(String sessionIdentifier, Integer page, Integer perPage, String apiKey) {
        try {
            // Validate input
            if (sessionIdentifier == null || sessionIdentifier.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Session identifier cannot be null or empty");
                return errorResponse;
            }
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // WASender API requires session ID (integer) in the URL, not the name
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            // Build URL with pagination parameters - use session-logs endpoint
            String logsUrl = wasenderBaseUrl + "/whatsapp-sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8) + "/session-logs";
            if (page != null && page > 0) {
                logsUrl += "?page=" + page;
            } else {
                logsUrl += "?page=1";
            }
            if (perPage != null && perPage > 0) {
                logsUrl += "&per_page=" + perPage;
            } else {
                logsUrl += "&per_page=10";
            }
            
            log.info("Fetching session logs for session: {} (page: {}, per_page: {})", 
                sessionId, page != null ? page : 1, perPage != null ? perPage : 10);
            
            String response = webClient.get()
                .uri(logsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            // Parse response
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            }
            
            if (jsonNode.has("data")) {
                result.put("data", objectMapper.convertValue(jsonNode.get("data"), Map.class));
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            log.error("WASender API error fetching session logs for session '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error fetching session logs from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Get all WhatsApp sessions.
     * @param apiKey The WASender API key to use
     * @return Map containing list of all sessions
     */
    public Map<String, Object> getAllSessions(String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            String sessionsUrl = wasenderBaseUrl + "/whatsapp-sessions";
            
            log.info("Fetching all WhatsApp sessions");
            
            String response = webClient.get()
                .uri(sessionsUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            // Parse response
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            }
            
            if (jsonNode.has("data")) {
                JsonNode dataNode = jsonNode.get("data");
                // Check if data is an array (list of sessions)
                if (dataNode.isArray()) {
                    result.put("data", objectMapper.convertValue(dataNode, 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)));
                } else if (dataNode.isObject()) {
                    result.put("data", objectMapper.convertValue(dataNode, Map.class));
                } else {
                    // For primitive values, convert directly
                    result.put("data", objectMapper.convertValue(dataNode, Object.class));
                }
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            log.error("WASender API error fetching all sessions: Status={}, response redacted", 
                e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error fetching all sessions from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Get session details by session ID or name.
     * @param sessionIdentifier Session ID (integer as string) or session name
     * @param apiKey The WASender API key to use
     * @return Map containing session details
     */
    public Map<String, Object> getSessionDetails(String sessionIdentifier, String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            String sessionUrl = wasenderBaseUrl + "/whatsapp-sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            
            log.info("Fetching session details for: {} (ID: {})", sessionIdentifier, sessionId);
            
            String response = webClient.get()
                .uri(sessionUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(2))
                    .filter(throwable -> !(throwable instanceof WebClientResponseException 
                        && ((WebClientResponseException) throwable).getStatusCode().is4xxClientError())))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            // Log raw response for debugging (truncated if too long)
            if (log.isDebugEnabled()) {
                String responsePreview = response.length() > 500 
                    ? response.substring(0, 500) + "... (truncated)" 
                    : response;
                log.debug("Raw session details response for {}: {}", sessionId, responsePreview);
            }
            
            // Parse response
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            }
            
            if (jsonNode.has("data")) {
                JsonNode dataNode = jsonNode.get("data");
                result.put("data", objectMapper.convertValue(dataNode, Map.class));
                
                // Log available fields in data for debugging
                if (log.isDebugEnabled() && dataNode.isObject()) {
                    java.util.Iterator<String> fieldNames = dataNode.fieldNames();
                    java.util.List<String> fields = new java.util.ArrayList<>();
                    while (fieldNames.hasNext()) {
                        fields.add(fieldNames.next());
                    }
                    log.debug("Available fields in session details data: {}", fields);
                }
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            log.error("WASender API error fetching session details for '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error fetching session details from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Delete a WhatsApp session.
     * @param sessionIdentifier Session ID (integer as string) or session name
     * @param apiKey The WASender API key to use
     * @return Map containing deletion result
     */
    public Map<String, Object> deleteSession(String sessionIdentifier, String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }
            
            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            String deleteUrl = wasenderBaseUrl + "/whatsapp-sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            
            log.info("Deleting session: {}", sessionId);
            
            String response = webClient.delete()
                .uri(deleteUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Session deleted successfully");
            if (response != null && !response.trim().isEmpty()) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(response);
                    if (jsonNode.has("message")) {
                        result.put("message", jsonNode.get("message").asText());
                    }
                } catch (Exception e) {
                    // Ignore parsing errors
                }
            }
            
            return result;
            
        } catch (WebClientResponseException e) {
            String errorMessage = e.getMessage();
            String responseBody = e.getResponseBodyAsString();
            
            log.error("WASender API error deleting session '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            
            if (responseBody != null && !responseBody.trim().isEmpty()) {
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    if (errorJson.has("message")) {
                        errorResponse.put("error", errorJson.get("message").asText());
                        if (errorJson.has("help")) {
                            errorResponse.put("help", errorJson.get("help").asText());
                        }
                    } else if (errorJson.has("error")) {
                        errorResponse.put("error", errorJson.get("error").asText());
                    } else {
                        errorResponse.put("error", "Provider error (details redacted)");
                    }
                } catch (Exception parseException) {
                    errorResponse.put("error", "Provider error (details redacted)");
                }
            } else {
                errorResponse.put("error", errorMessage);
            }
            
            errorResponse.put("statusCode", e.getStatusCode().value());
            errorResponse.put("statusText", e.getStatusCode().toString());
            
            return errorResponse;
            
        } catch (Exception e) {
            log.error("Error deleting session from WASender", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Update a WhatsApp session.
     * @param sessionIdentifier Session ID (integer as string) or session name
     * @param apiKey The WASender API key to use
     * @param updateData Map containing fields to update (name, log_messages, webhook_enabled, webhook_events, etc.)
     * @return Map containing update result
     */
    public Map<String, Object> updateSession(String sessionIdentifier, String apiKey, Map<String, Object> updateData) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }

            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            String updateUrl = wasenderBaseUrl + "/whatsapp-sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
            
            log.info("Updating WASender session: {}", sessionId);
            
            String response = webClient.put()
                .uri(updateUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(updateData)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            } else {
                result.put("success", true);
            }
            
            if (jsonNode.has("data")) {
                result.put("data", objectMapper.convertValue(jsonNode.get("data"), Map.class));
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            if (jsonNode.has("message")) {
                result.put("message", jsonNode.get("message").asText());
            }
            
            log.info("Successfully updated WASender session: {}", sessionId);
            return result;
            
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("Unauthorized error updating WASender session '{}': response redacted", sessionIdentifier);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Unauthorized: Invalid API key");
            return errorResponse;
        } catch (WebClientResponseException.NotFound e) {
            log.error("Session not found: {}", sessionIdentifier);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Session not found");
            return errorResponse;
        } catch (WebClientResponseException e) {
            log.error("WASender API error updating session '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "WASender API error (details redacted)");
            return errorResponse;
        } catch (Exception e) {
            log.error("Error updating WASender session '{}'", sessionIdentifier, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Disconnect a WhatsApp session.
     * @param sessionIdentifier Session ID (integer as string) or session name
     * @param apiKey The WASender API key to use
     * @return Map containing disconnect result
     */
    public Map<String, Object> disconnectSession(String sessionIdentifier, String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }

            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            // CRITICAL: Always resolve session name to numeric ID before calling WASender endpoints
            String sessionId = ensureNumericSessionId(sessionIdentifier, apiKey);
            
            String disconnectUrl = wasenderBaseUrl + "/whatsapp-sessions/" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8) + "/disconnect";
            
            log.info("Disconnecting WASender session: {}", sessionId);
            
            String response = webClient.post()
                .uri(disconnectUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            } else {
                result.put("success", true);
            }
            
            if (jsonNode.has("data")) {
                result.put("data", objectMapper.convertValue(jsonNode.get("data"), Map.class));
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            if (jsonNode.has("message")) {
                result.put("message", jsonNode.get("message").asText());
            }
            
            log.info("Successfully disconnected WASender session: {}", sessionId);
            return result;
            
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("Unauthorized error disconnecting WASender session '{}': response redacted", sessionIdentifier);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Unauthorized: Invalid API key");
            return errorResponse;
        } catch (WebClientResponseException.NotFound e) {
            log.error("Session not found: {}", sessionIdentifier);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Session not found");
            return errorResponse;
        } catch (WebClientResponseException e) {
            log.error("WASender API error disconnecting session '{}': Status={}, response redacted", 
                sessionIdentifier, e.getStatusCode());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "WASender API error (details redacted)");
            return errorResponse;
        } catch (Exception e) {
            log.error("Error disconnecting WASender session '{}'", sessionIdentifier, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Get WhatsApp session status.
     * @param apiKey The WASender API key to use
     * @return Map containing session status information
     */
    public Map<String, Object> getSessionStatus(String apiKey) {
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("WASender API key is required");
            }

            WebClient webClient = webClientBuilder.baseUrl(wasenderBaseUrl).build();
            
            String statusUrl = wasenderBaseUrl + "/status";
            
            log.info("Fetching WASender session status");
            
            String response = webClient.get()
                .uri(statusUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            if (response == null || response.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Empty response from WASender API");
                return errorResponse;
            }
            
            JsonNode jsonNode = objectMapper.readTree(response);
            Map<String, Object> result = new HashMap<>();
            
            if (jsonNode.has("success")) {
                result.put("success", jsonNode.get("success").asBoolean());
            } else {
                result.put("success", true);
            }
            
            if (jsonNode.has("data")) {
                result.put("data", objectMapper.convertValue(jsonNode.get("data"), Map.class));
            } else {
                result.put("data", objectMapper.convertValue(jsonNode, Map.class));
            }
            
            log.info("Successfully fetched WASender session status");
            return result;
            
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("Unauthorized error fetching WASender session status: response redacted");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Unauthorized: Invalid API key");
            return errorResponse;
        } catch (WebClientResponseException e) {
            log.error("WASender API error fetching session status: Status={}, response redacted", 
                e.getStatusCode());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "WASender API error (details redacted)");
            return errorResponse;
        } catch (Exception e) {
            log.error("Error fetching WASender session status", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
}


