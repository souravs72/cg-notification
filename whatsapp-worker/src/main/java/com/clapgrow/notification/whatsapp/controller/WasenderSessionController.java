package com.clapgrow.notification.whatsapp.controller;

import com.clapgrow.notification.whatsapp.service.WasenderQRService;
import com.clapgrow.notification.whatsapp.service.WasenderSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API controller for WASender session management operations.
 * 
 * ⚠️ ARCHITECTURE: This controller exposes provider control-plane operations
 * from whatsapp-worker. notification-api calls these endpoints instead of
 * directly calling WASender API, maintaining proper module boundaries.
 */
@RestController
@RequestMapping("/api/whatsapp/sessions")
@RequiredArgsConstructor
@Slf4j
public class WasenderSessionController {
    
    private final WasenderQRService wasenderQRService;
    private final WasenderSubscriptionService wasenderSubscriptionService;

    @GetMapping("/qrcode")
    public ResponseEntity<Map<String, Object>> getQRCode(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(name = "sessionName", required = false) String sessionName,
            @RequestParam(name = "sessionId", required = false) String sessionId) {
        String apiKey = extractApiKey(authorization);
        String identifier = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : sessionName;
        if (identifier == null || identifier.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Either sessionName or sessionId must be provided"
            ));
        }
        Map<String, Object> result = wasenderQRService.getQRCode(identifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionName}/connect")
    public ResponseEntity<Map<String, Object>> connectSession(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String sessionName) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.connectSession(sessionName, apiKey);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody Map<String, Object> request) {
        String apiKey = extractApiKey(authorization);
        String sessionName = (String) request.get("sessionName");
        String phoneNumber = (String) request.get("phoneNumber");
        Boolean accountProtection = getBoolean(request.get("accountProtection"), true);
        Boolean logMessages = getBoolean(request.get("logMessages"), true);
        String webhookUrl = (String) request.get("webhookUrl");
        Boolean webhookEnabled = getBoolean(request.get("webhookEnabled"), null);
        String[] webhookEvents = extractStringArray(request.get("webhookEvents"));
        Boolean readIncomingMessages = getBoolean(request.get("readIncomingMessages"), null);
        Boolean autoRejectCalls = getBoolean(request.get("autoRejectCalls"), null);
        Boolean ignoreGroups = getBoolean(request.get("ignoreGroups"), null);
        Boolean ignoreChannels = getBoolean(request.get("ignoreChannels"), null);
        Boolean ignoreBroadcasts = getBoolean(request.get("ignoreBroadcasts"), null);
        
        Map<String, Object> result = wasenderQRService.createSession(
            sessionName, phoneNumber, accountProtection, logMessages,
            webhookUrl, webhookEnabled, webhookEvents,
            readIncomingMessages, autoRejectCalls,
            ignoreGroups, ignoreChannels, ignoreBroadcasts, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/message-logs")
    public ResponseEntity<Map<String, Object>> getMessageLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "sessionName", required = false) String sessionName,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "per_page", defaultValue = "10") Integer perPage) {
        String apiKey = extractApiKey(authorization);
        String identifier = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : sessionName;
        if (identifier == null || identifier.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Either sessionName or sessionId must be provided"
            ));
        }
        Map<String, Object> result = wasenderQRService.getMessageLogs(identifier, page, perPage, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/session-logs")
    public ResponseEntity<Map<String, Object>> getSessionLogs(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "sessionName", required = false) String sessionName,
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "per_page", defaultValue = "10") Integer perPage) {
        String apiKey = extractApiKey(authorization);
        String identifier = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : sessionName;
        if (identifier == null || identifier.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Either sessionName or sessionId must be provided"
            ));
        }
        Map<String, Object> result = wasenderQRService.getSessionLogs(identifier, page, perPage, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.getAllSessions(apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{sessionIdentifier}")
    public ResponseEntity<Map<String, Object>> getSessionDetails(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String sessionIdentifier) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.getSessionDetails(sessionIdentifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{sessionIdentifier}")
    public ResponseEntity<Map<String, Object>> deleteSession(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String sessionIdentifier) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.deleteSession(sessionIdentifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{sessionIdentifier}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String sessionIdentifier,
            @RequestBody Map<String, Object> updateData) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.updateSession(sessionIdentifier, apiKey, updateData);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionIdentifier}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectSession(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String sessionIdentifier) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.disconnectSession(sessionIdentifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String apiKey = extractApiKey(authorization);
        Map<String, Object> result = wasenderQRService.getSessionStatus(apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/subscription")
    public ResponseEntity<Map<String, Object>> getSubscriptionInfo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String apiKey = extractApiKey(authorization);
        try {
            Map<String, Object> result = wasenderSubscriptionService.getSubscriptionInfo(apiKey);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            // Extract error category from message prefix (TEMPORARY:, AUTH:, PERMANENT:)
            String message = e.getMessage();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", message != null && message.contains(":") ? message.substring(message.indexOf(":") + 1) : message);
            if (message != null) {
                if (message.startsWith("TEMPORARY:")) {
                    errorResponse.put("category", "TEMPORARY");
                } else if (message.startsWith("AUTH:")) {
                    errorResponse.put("category", "AUTH");
                } else if (message.startsWith("PERMANENT:")) {
                    errorResponse.put("category", "PERMANENT");
                }
            }
            int statusCode = message != null && message.startsWith("AUTH:") ? 401 : 500;
            return ResponseEntity.status(statusCode).body(errorResponse);
        }
    }

    /**
     * Extract API key from Authorization header (Bearer token format).
     */
    private String extractApiKey(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header. Expected 'Bearer <api-key>'");
    }

    /**
     * Safely extract boolean from request object.
     */
    private Boolean getBoolean(Object value, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.valueOf(value.toString());
    }

    /**
     * Safely extract string array from request object.
     */
    @SuppressWarnings("unchecked")
    private String[] extractStringArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String[]) {
            return (String[]) value;
        }
        if (value instanceof java.util.List) {
            java.util.List<String> list = (java.util.List<String>) value;
            return list.toArray(new String[0]);
        }
        return null;
    }
}

