package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.common.provider.ProviderErrorCategory;
import com.clapgrow.notification.common.provider.ProviderName;
import com.clapgrow.notification.common.provider.WhatsAppProvider;
import com.clapgrow.notification.common.provider.WhatsAppResult;
import com.clapgrow.notification.whatsapp.entity.FrappeSite;
import com.clapgrow.notification.whatsapp.entity.WhatsAppSession;
import com.clapgrow.notification.whatsapp.model.NotificationPayload;
import com.clapgrow.notification.whatsapp.model.WasenderMessageRequest;
import com.clapgrow.notification.whatsapp.repository.FrappeSiteRepository;
import com.clapgrow.notification.whatsapp.repository.WhatsAppSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WasenderService implements WhatsAppProvider<NotificationPayload> {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final WhatsAppSessionRepository whatsAppSessionRepository;
    private final FrappeSiteRepository frappeSiteRepository;
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    @Override
    public ProviderName getProviderName() {
        return ProviderName.WASENDER;
    }

    @Override
    public WhatsAppResult sendMessage(NotificationPayload payload) {
        String originalRecipient = payload.getRecipient();
        WasenderMessageRequest request = null;
        String requestUrl = wasenderBaseUrl + "/send-message";
        
        try {
            // Resolve API key from database (priority: whatsappSessionName -> siteId)
            String apiKey = resolveApiKey(payload);
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                String errorMsg = "WASender API key could not be resolved. Please ensure whatsappSessionName or siteId is provided and session is configured.";
                log.error("WASender API key could not be resolved for recipient: {}", originalRecipient);
                return WhatsAppResult.createFailure(
                    errorMsg,
                    String.format("Missing API key for recipient: %s. URL: %s. Resolution attempted: whatsappSessionName=%s, siteId=%s", 
                        originalRecipient, requestUrl, payload.getWhatsappSessionName(), payload.getSiteId()),
                    null,
                    null,
                    ProviderErrorCategory.CONFIG
                );
            }
            
            request = buildWasenderRequest(payload);
            
            // Log request context only - never full body (may contain PII; API key is in header)
            log.info("Sending WhatsApp message to WASender API. URL: {}, Recipient: {}, Session: {}", 
                requestUrl, request.getTo() != null ? request.getTo() : originalRecipient,
                request.getWhatsappSession() != null ? request.getWhatsappSession() : "N/A");
            
            String response = webClient.post()
                .uri(requestUrl)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();
            
            // SECURITY: Do not log raw API response - may contain tokens/session data
            log.info("WhatsApp message sent successfully to {} (response length={})", 
                request.getTo(), response != null ? response.length() : 0);
            return WhatsAppResult.createSuccess();
            
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.TooManyRequests e) {
            String errorBody = e.getResponseBodyAsString();
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            String errorMsg = String.format("Rate limit exceeded (429 Too Many Requests) for recipient: %s", recipient);
            // SECURITY: Do not include raw response body in details or logs
            String errorDetails = String.format(
                "HTTP Status: 429 Too Many Requests%nURL: %s%nRecipient: %s%nResponse body: [redacted]%nRequest: to=%s, session=%s",
                requestUrl, recipient,
                request != null ? request.getTo() : originalRecipient,
                request != null ? request.getWhatsappSession() : "N/A"
            );
            // SECURITY: Do not log raw response body - may contain secrets
            log.warn("Rate limit exceeded (429) for WhatsApp message to {}", recipient);
            return WhatsAppResult.createFailure(
                errorMsg, errorDetails, 429, errorBody, ProviderErrorCategory.TEMPORARY
            );
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized e) {
            String errorBody = e.getResponseBodyAsString();
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            String errorMsg = String.format("Invalid API key (401 Unauthorized) for recipient: %s", recipient);
            // SECURITY: Do not include raw response body in details or logs
            String errorDetails = String.format(
                "HTTP Status: 401 Unauthorized%nURL: %s%nRecipient: %s%nResponse body: [redacted]",
                requestUrl, recipient
            );
            log.error("Invalid API key (401) for WhatsApp message to {}", recipient);
            return WhatsAppResult.createFailure(
                errorMsg, errorDetails, 401, errorBody, ProviderErrorCategory.AUTH
            );
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden e) {
            String errorBody = e.getResponseBodyAsString();
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            String errorMsg = String.format("API key access forbidden (403 Forbidden) for recipient: %s", recipient);
            // SECURITY: Do not include raw response body in details or logs
            String errorDetails = String.format(
                "HTTP Status: 403 Forbidden%nURL: %s%nRecipient: %s%nResponse body: [redacted]",
                requestUrl, recipient
            );
            log.error("API key access forbidden (403) for WhatsApp message to {}", recipient);
            return WhatsAppResult.createFailure(
                errorMsg, errorDetails, 403, errorBody, ProviderErrorCategory.AUTH
            );
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            String recipient = request != null && request.getTo() != null ? request.getTo() : originalRecipient;
            ProviderErrorCategory errorCategory = categorizeError(e.getStatusCode().value());
            String errorMsg = String.format("HTTP error %s sending WhatsApp message to %s via WASender API", 
                e.getStatusCode(), recipient);
            // SECURITY: Do not include raw response body in details or logs
            String errorDetails = String.format(
                "HTTP Status: %s%nURL: %s%nRecipient: %s%nResponse body: [redacted]%nRequest: to=%s, session=%s, hasText=%s, hasImage=%s, hasVideo=%s, hasDocument=%s",
                e.getStatusCode(), requestUrl, recipient,
                request != null ? request.getTo() : originalRecipient,
                request != null ? request.getWhatsappSession() : "N/A",
                request != null && request.getText() != null,
                request != null && request.getImageUrl() != null,
                request != null && request.getVideoUrl() != null,
                request != null && request.getDocumentUrl() != null
            );
            log.error("HTTP error {} sending WhatsApp message to {} via WASender API. Response redacted.", 
                e.getStatusCode(), recipient);
            return WhatsAppResult.createFailure(
                errorMsg, errorDetails, e.getStatusCode().value(), errorBody, errorCategory
            );
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
            return WhatsAppResult.createFailure(
                errorMsg, errorDetails, null, null, ProviderErrorCategory.TEMPORARY
            );
        }
    }
    
    /**
     * Categorize WASender API error based on HTTP status code.
     * 
     * @param statusCode HTTP status code
     * @return ProviderErrorCategory
     */
    private ProviderErrorCategory categorizeError(int statusCode) {
        // 401 Unauthorized, 403 Forbidden = AUTH errors
        if (statusCode == 401 || statusCode == 403) {
            return ProviderErrorCategory.AUTH;
        }
        // 429 Too Many Requests or 5xx Server Errors = TEMPORARY
        else if (statusCode == 429 || statusCode >= 500) {
            return ProviderErrorCategory.TEMPORARY;
        }
        // 4xx Client Errors (except 401/403) = PERMANENT
        else if (statusCode >= 400 && statusCode < 500) {
            return ProviderErrorCategory.PERMANENT;
        }
        // Unknown errors default to TEMPORARY
        else {
            return ProviderErrorCategory.TEMPORARY;
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
    
    /**
     * Resolve WASender API key from database.
     * 
     * Resolution priority:
     * 1. If whatsappSessionName is present AND siteId is present:
     *    - Verify siteId matches message tenant (caller responsibility)
     *    - Lookup FrappeSite by siteId
     *    - Verify payload's whatsappSessionName matches site's whatsappSessionName (tenant scoping)
     *    - Lookup WhatsAppSession by name -> use sessionApiKey
     * 2. Else if siteId is present:
     *    - Verify siteId matches message tenant (caller responsibility)
     *    - Lookup FrappeSite by siteId -> get whatsappSessionName -> lookup WhatsAppSession -> use sessionApiKey
     * 3. Else: return null (fail fast with CONFIG error)
     * 
     * ⚠️ SECURITY: API keys are resolved from database, never from payload or environment variables.
     * This ensures credentials are not exposed in Kafka topics, logs, or DLQs.
     * 
     * ⚠️ TENANT ISOLATION: Session resolution is tenant-scoped via siteId verification.
     * Caller MUST verify payload.siteId matches message_logs.site_id before calling this method.
     * 
     * ⚠️ KAFKA COMPATIBILITY: This change requires coordinated deployment of producer (notification-api)
     * and consumer (whatsapp-worker) or a short dual-read window. The producer no longer includes
     * wasenderApiKey in the payload, and the consumer must resolve it from the database.
     * 
     * ⚠️ TENANT INVARIANT: Each site has at most one active WhatsApp session used for message delivery.
     * The resolution logic assumes this invariant when looking up sessions via siteId.
     * 
     * @param payload Notification payload containing whatsappSessionName and/or siteId
     * @return API key if resolved, null otherwise
     */
    private String resolveApiKey(NotificationPayload payload) {
        // ⚠️ TENANT ISOLATION: siteId is required for tenant-scoped session resolution
        if (payload.getSiteId() == null) {
            log.error("siteId is required for tenant-scoped API key resolution. Payload missing siteId.");
            return null;
        }
        
        try {
            // Lookup site for tenant verification and session name resolution
            Optional<FrappeSite> siteOpt = frappeSiteRepository.findByIdAndIsDeletedFalse(payload.getSiteId());
            
            if (siteOpt.isEmpty()) {
                log.error("FrappeSite not found for siteId: {}", payload.getSiteId());
                return null;
            }
            
            FrappeSite site = siteOpt.get();
            String siteSessionName = site.getWhatsappSessionName();
            String payloadSessionName = payload.getWhatsappSessionName();
            
            // Determine which session name to use (tenant-scoped)
            String sessionNameToResolve = null;
            
            if (payloadSessionName != null && !payloadSessionName.trim().isEmpty()) {
                // Payload specifies session name - verify it matches site's session (tenant scoping)
                if (siteSessionName != null && !siteSessionName.trim().isEmpty()) {
                    if (!payloadSessionName.trim().equals(siteSessionName.trim())) {
                        log.error("SECURITY: Tenant isolation violation - payload sessionName '{}' does not match site's sessionName '{}' for siteId={}", 
                            payloadSessionName, siteSessionName, payload.getSiteId());
                        return null;
                    }
                }
                // Use payload session name (verified to match site or site has none)
                sessionNameToResolve = payloadSessionName.trim();
            } else if (siteSessionName != null && !siteSessionName.trim().isEmpty()) {
                // Use site's configured session name
                sessionNameToResolve = siteSessionName.trim();
            } else {
                log.warn("No WhatsApp session name available: siteId={}, site has no whatsappSessionName configured", 
                    payload.getSiteId());
                return null;
            }
            
            // Resolve session API key using tenant-scoped session name
            Optional<WhatsAppSession> sessionOpt = whatsAppSessionRepository
                .findFirstBySessionNameAndIsDeletedFalse(sessionNameToResolve);
            
            if (sessionOpt.isEmpty()) {
                log.warn("WhatsAppSession not found for sessionName: {} (resolved from siteId={})", 
                    sessionNameToResolve, payload.getSiteId());
                return null;
            }
            
            WhatsAppSession session = sessionOpt.get();
            if (session.getSessionApiKey() == null || session.getSessionApiKey().trim().isEmpty()) {
                log.warn("WhatsAppSession found but has no API key: sessionName={}, status={}", 
                    sessionNameToResolve, session.getStatus());
                return null;
            }
            
            log.debug("Resolved API key from tenant-scoped session lookup: siteId={}, sessionName={}", 
                payload.getSiteId(), sessionNameToResolve);
            return session.getSessionApiKey().trim();
            
        } catch (DataAccessException e) {
            log.error("Database error resolving API key for siteId={}, whatsappSessionName={}: {}", 
                payload.getSiteId(), payload.getWhatsappSessionName(), e.getMessage(), e);
            return null; // Fail fast - will be retried by KafkaRetryService
        } catch (Exception e) {
            log.error("Unexpected error resolving API key for siteId={}, whatsappSessionName={}: {}", 
                payload.getSiteId(), payload.getWhatsappSessionName(), e.getMessage(), e);
            return null; // Fail fast - will be retried by KafkaRetryService
        }
    }
}

