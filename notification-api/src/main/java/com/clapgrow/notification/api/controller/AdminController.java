package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.AdminDashboardResponse;
import com.clapgrow.notification.api.dto.MessageDetailResponse;
import com.clapgrow.notification.api.dto.SiteRegistrationRequest;
import com.clapgrow.notification.api.dto.SendGridApiKeyRequest;
import com.clapgrow.notification.api.dto.SendGridApiKeyResponse;
import com.clapgrow.notification.api.dto.WasenderApiKeyRequest;
import com.clapgrow.notification.api.dto.WasenderApiKeyResponse;
import com.clapgrow.notification.api.entity.User;
import com.clapgrow.notification.api.service.AdminAuthService;
import com.clapgrow.notification.api.service.AdminService;
import com.clapgrow.notification.api.service.SendGridConfigService;
import com.clapgrow.notification.api.service.SiteService;
import com.clapgrow.notification.api.service.UserService;
import com.clapgrow.notification.api.service.UserWasenderService;
import com.clapgrow.notification.api.service.WasenderConfigService;
import com.clapgrow.notification.api.service.WasenderQRService;
import jakarta.servlet.http.HttpSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Administrative API endpoints for managing sites, sessions, and viewing system metrics")
public class AdminController {
    
    private final AdminService adminService;
    private final SiteService siteService;
    private final WasenderQRService wasenderQRService;
    private final AdminAuthService adminAuthService;
    private final WasenderConfigService wasenderConfigService;
    private final SendGridConfigService sendGridConfigService;
    private final UserWasenderService userWasenderService;
    private final UserService userService;
    private final com.clapgrow.notification.api.service.WhatsAppSessionService whatsAppSessionService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        // Get user info from session
        User user = userWasenderService.getCurrentUser(session)
            .orElseThrow(() -> new IllegalStateException("User not found in session"));
        
        model.addAttribute("user", user);
        AdminDashboardResponse metrics = adminService.getDashboardMetrics();
        model.addAttribute("metrics", metrics);
        return "admin/dashboard";
    }

    @GetMapping("/sites")
    public String sites() {
        return "admin/sites";
    }

    @GetMapping("/sessions")
    public String sessions() {
        return "admin/sessions";
    }

    @GetMapping("/api/metrics")
    @Operation(
            summary = "Get admin dashboard metrics",
            description = "Retrieves aggregated metrics for the admin dashboard including total sites, messages, and system statistics."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = AdminDashboardResponse.class)))
    })
    public ResponseEntity<AdminDashboardResponse> getMetrics() {
        AdminDashboardResponse response = adminService.getDashboardMetrics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/messages/recent")
    @Operation(
            summary = "Get recent messages",
            description = "Retrieves the most recent messages across all sites."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recent messages retrieved successfully")
    })
    public ResponseEntity<List<MessageDetailResponse>> getRecentMessages(
            @Parameter(description = "Maximum number of messages to retrieve", example = "50")
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<MessageDetailResponse> messages = adminService.getRecentMessages(limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/api/messages/failed")
    @Operation(
            summary = "Get failed messages",
            description = "Retrieves messages that have failed to deliver."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Failed messages retrieved successfully")
    })
    public ResponseEntity<List<MessageDetailResponse>> getFailedMessages(
            @Parameter(description = "Maximum number of messages to retrieve", example = "50")
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<MessageDetailResponse> messages = adminService.getFailedMessages(limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/api/messages/failed/export")
    @Operation(
            summary = "Export failed messages error log",
            description = "Downloads a detailed error log file containing all failed messages with their error details."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Error log file downloaded successfully"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<org.springframework.core.io.Resource> exportFailedMessagesLog(
            @Parameter(description = "Maximum number of messages to include", example = "1000")
            @RequestParam(name = "limit", defaultValue = "1000") int limit) {
        try {
            List<MessageDetailResponse> messages = adminService.getFailedMessages(limit);
            
            // Build detailed error log content
            StringBuilder logContent = new StringBuilder();
            logContent.append("=".repeat(80)).append("\n");
            logContent.append("FAILED MESSAGES ERROR LOG\n");
            logContent.append("Generated: ").append(java.time.LocalDateTime.now()).append("\n");
            logContent.append("Total Failed Messages: ").append(messages.size()).append("\n");
            logContent.append("=".repeat(80)).append("\n\n");
            
            for (int i = 0; i < messages.size(); i++) {
                MessageDetailResponse msg = messages.get(i);
                logContent.append(String.format("Message #%d\n", i + 1));
                logContent.append("-".repeat(80)).append("\n");
                logContent.append(String.format("Message ID: %s\n", msg.getMessageId()));
                logContent.append(String.format("Site: %s\n", msg.getSiteName() != null ? msg.getSiteName() : "N/A"));
                logContent.append(String.format("Channel: %s\n", msg.getChannel()));
                logContent.append(String.format("Recipient: %s\n", msg.getRecipient()));
                logContent.append(String.format("Status: %s\n", msg.getStatus()));
                logContent.append(String.format("Created At: %s\n", msg.getCreatedAt() != null ? msg.getCreatedAt() : "N/A"));
                logContent.append(String.format("Sent At: %s\n", msg.getSentAt() != null ? msg.getSentAt() : "N/A"));
                
                if (msg.getSubject() != null && !msg.getSubject().isEmpty()) {
                    logContent.append(String.format("Subject: %s\n", msg.getSubject()));
                }
                
                if (msg.getBody() != null && !msg.getBody().isEmpty()) {
                    logContent.append(String.format("Body: %s\n", msg.getBody()));
                }
                
                // Detailed error message
                if (msg.getErrorMessage() != null && !msg.getErrorMessage().isEmpty()) {
                    logContent.append("\nERROR DETAILS:\n");
                    logContent.append(msg.getErrorMessage()).append("\n");
                } else {
                    logContent.append("\nERROR DETAILS: No error message available\n");
                }
                
                logContent.append("\n").append("=".repeat(80)).append("\n\n");
            }
            
            // Create a resource from the log content
            byte[] logBytes = logContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            org.springframework.core.io.InputStreamResource resource = 
                new org.springframework.core.io.InputStreamResource(new java.io.ByteArrayInputStream(logBytes));
            
            String filename = String.format("error-log-%s.txt", 
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
            
            return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8")
                .contentLength(logBytes.length)
                .body(resource);
                
        } catch (Exception e) {
            log.error("Error exporting failed messages log", e);
            throw new RuntimeException("Failed to export error log: " + e.getMessage(), e);
        }
    }

    @GetMapping("/api/messages/scheduled")
    @Operation(
            summary = "Get scheduled messages",
            description = "Retrieves messages that are scheduled for future delivery."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Scheduled messages retrieved successfully")
    })
    public ResponseEntity<List<MessageDetailResponse>> getScheduledMessages(
            @Parameter(description = "Maximum number of messages to retrieve", example = "50")
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<MessageDetailResponse> messages = adminService.getScheduledMessages(limit);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/api/sites/{siteId}")
    @Operation(
            summary = "Delete a site",
            description = "Deletes a site and all associated data. Requires admin authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Site deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing admin key"),
            @ApiResponse(responseCode = "404", description = "Site not found")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<?> deleteSite(
            @Parameter(description = "Admin API key for authentication", required = true)
            @RequestHeader(name = "X-Admin-Key", required = true) String adminKey,
            @Parameter(description = "Site ID to delete", required = true)
            @PathVariable(name = "siteId") String siteId) {
        try {
            adminAuthService.validateAdminKey(adminKey);
            siteService.deleteSite(java.util.UUID.fromString(siteId));
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Site deleted successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (SecurityException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(401).body(errorResponse);
        }
    }

    @GetMapping("/api/whatsapp/qrcode")
    public ResponseEntity<Map<String, Object>> getWhatsAppQRCode(
            @RequestParam(name = "sessionName", required = false) String sessionName,
            @RequestParam(name = "sessionId", required = false) String sessionId,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        // Use session ID if provided (preferred), otherwise use session name
        String identifier = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : sessionName;
        if (identifier == null || identifier.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Either sessionName or sessionId must be provided");
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> result = wasenderQRService.getQRCode(identifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/whatsapp/session/{sessionName}/connect")
    public ResponseEntity<Map<String, Object>> connectWhatsAppSession(
            @PathVariable(name = "sessionName") String sessionName,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.connectSession(sessionName, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/whatsapp/message-logs")
    public ResponseEntity<Map<String, Object>> getMessageLogs(
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "sessionName", required = false) String sessionName,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "per_page", required = false, defaultValue = "10") Integer perPage,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        // Use session ID if provided (preferred), otherwise use session name
        String identifier = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : sessionName;
        
        if (identifier == null || identifier.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Either sessionName or sessionId must be provided");
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> result = wasenderQRService.getMessageLogs(identifier, page, perPage, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/whatsapp/session-logs")
    public ResponseEntity<Map<String, Object>> getSessionLogs(
            @RequestParam(name = "sessionId", required = false) String sessionId,
            @RequestParam(name = "sessionName", required = false) String sessionName,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "per_page", required = false, defaultValue = "10") Integer perPage,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        // Use session ID if provided (preferred), otherwise use session name
        String identifier = (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId : sessionName;
        
        if (identifier == null || identifier.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Either sessionName or sessionId must be provided");
            return ResponseEntity.badRequest().body(error);
        }
        
        Map<String, Object> result = wasenderQRService.getSessionLogs(identifier, page, perPage, apiKey);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/whatsapp/session")
    public ResponseEntity<Map<String, Object>> createWhatsAppSession(
            @RequestBody Map<String, Object> request,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        String sessionName = (String) request.get("sessionName");
        if (sessionName == null || sessionName.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Session name is required");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Required field: phone_number
        String phoneNumber = (String) request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Phone number is required (in international format, e.g., +1234567890)");
            return ResponseEntity.badRequest().body(error);
        }
        
        // Required fields with defaults
        Boolean accountProtection = request.get("accountProtection") != null 
            ? Boolean.valueOf(request.get("accountProtection").toString()) 
            : true;
        Boolean logMessages = request.get("logMessages") != null 
            ? Boolean.valueOf(request.get("logMessages").toString()) 
            : true;
        
        // Optional fields
        String webhookUrl = (String) request.get("webhookUrl");
        Boolean webhookEnabled = request.get("webhookEnabled") != null 
            ? Boolean.valueOf(request.get("webhookEnabled").toString()) 
            : null;
        String[] webhookEvents = null;
        if (request.get("webhookEvents") != null) {
            Object eventsObj = request.get("webhookEvents");
            if (eventsObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<String> eventsList = (java.util.List<String>) eventsObj;
                webhookEvents = eventsList.toArray(new String[0]);
            } else if (eventsObj instanceof String[]) {
                webhookEvents = (String[]) eventsObj;
            }
        }
        Boolean readIncomingMessages = request.get("readIncomingMessages") != null 
            ? Boolean.valueOf(request.get("readIncomingMessages").toString()) 
            : null;
        Boolean autoRejectCalls = request.get("autoRejectCalls") != null 
            ? Boolean.valueOf(request.get("autoRejectCalls").toString()) 
            : null;
        Boolean ignoreGroups = request.get("ignoreGroups") != null 
            ? Boolean.valueOf(request.get("ignoreGroups").toString()) 
            : null;
        Boolean ignoreChannels = request.get("ignoreChannels") != null 
            ? Boolean.valueOf(request.get("ignoreChannels").toString()) 
            : null;
        Boolean ignoreBroadcasts = request.get("ignoreBroadcasts") != null 
            ? Boolean.valueOf(request.get("ignoreBroadcasts").toString()) 
            : null;
        
        Map<String, Object> result = wasenderQRService.createSession(
            sessionName, phoneNumber, accountProtection, logMessages,
            webhookUrl, webhookEnabled, webhookEvents,
            readIncomingMessages, autoRejectCalls,
            ignoreGroups, ignoreChannels, ignoreBroadcasts, apiKey);
        
        // Save session to database
        if (Boolean.TRUE.equals(result.get("success"))) {
            try {
                // Extract sessionId from result - it's directly in the result, not in data
                String sessionId = result.get("sessionId") != null 
                    ? result.get("sessionId").toString() 
                    : null;
                String actualSessionName = result.get("sessionName") != null
                    ? result.get("sessionName").toString()
                    : sessionName;
                
                // Also try to parse from response if available
                if (sessionId == null && result.get("response") != null) {
                    try {
                        String responseStr = result.get("response").toString();
                        if (responseStr != null && !responseStr.trim().isEmpty()) {
                            JsonNode responseJson = objectMapper.readTree(responseStr);
                            if (responseJson.has("data")) {
                                JsonNode dataNode = responseJson.get("data");
                                if (dataNode.isObject() && dataNode.has("id")) {
                                    sessionId = dataNode.get("id").asText();
                                }
                            } else if (responseJson.has("id")) {
                                sessionId = responseJson.get("id").asText();
                            }
                        }
                    } catch (Exception parseEx) {
                        log.debug("Could not parse session ID from response", parseEx);
                    }
                }
                
                com.clapgrow.notification.api.entity.WhatsAppSession savedSession = whatsAppSessionService.saveSession(
                    sessionId != null ? sessionId : actualSessionName,
                    actualSessionName,
                    phoneNumber,
                    accountProtection,
                    logMessages,
                    webhookUrl,
                    webhookEvents,
                    session
                );
                log.info("Session saved to database: {} (ID: {})", actualSessionName, sessionId);
                
                // First, try to get API key from the creation response
                String sessionApiKeyFromResponse = null;
                if (result.get("sessionApiKey") != null) {
                    sessionApiKeyFromResponse = result.get("sessionApiKey").toString();
                    log.info("Found session API key in creation response for session: {} (ID: {})", actualSessionName, sessionId);
                } else if (result.get("response") != null) {
                    // Try to parse API key from response string
                    try {
                        String responseStr = result.get("response").toString();
                        if (responseStr != null && !responseStr.trim().isEmpty()) {
                            JsonNode responseJson = objectMapper.readTree(responseStr);
                            JsonNode dataNode = responseJson.has("data") ? responseJson.get("data") : responseJson;
                            if (dataNode.has("api_key")) {
                                sessionApiKeyFromResponse = dataNode.get("api_key").asText();
                                log.info("Extracted session API key from response JSON for session: {} (ID: {})", actualSessionName, sessionId);
                            } else if (dataNode.has("apiKey")) {
                                sessionApiKeyFromResponse = dataNode.get("apiKey").asText();
                                log.info("Extracted session API key from response JSON (apiKey field) for session: {} (ID: {})", actualSessionName, sessionId);
                            } else if (dataNode.has("token")) {
                                sessionApiKeyFromResponse = dataNode.get("token").asText();
                                log.info("Extracted session API key from response JSON (token field) for session: {} (ID: {})", actualSessionName, sessionId);
                            }
                        }
                    } catch (Exception parseEx) {
                        log.debug("Could not parse API key from response", parseEx);
                    }
                }
                
                // Save API key from creation response if found
                if (sessionApiKeyFromResponse != null && !sessionApiKeyFromResponse.trim().isEmpty()) {
                    try {
                        String identifierToUse = sessionId != null && !sessionId.trim().isEmpty() 
                            ? sessionId 
                            : actualSessionName;
                        whatsAppSessionService.updateSessionApiKey(identifierToUse, sessionApiKeyFromResponse.trim(), session);
                        log.info("Session API key successfully saved from creation response for session: {} (ID: {})", 
                                actualSessionName, sessionId);
                    } catch (Exception updateEx) {
                        log.error("Failed to save session API key from creation response for session: {} (ID: {}). Error: {}", 
                                actualSessionName, sessionId, updateEx.getMessage(), updateEx);
                    }
                }
                
                // If session API key still doesn't exist, fetch it using session details endpoint
                if (savedSession.getSessionApiKey() == null || savedSession.getSessionApiKey().trim().isEmpty()) {
                    if (sessionId != null) {
                        try {
                            log.info("Session API key not found, fetching from session details endpoint for session ID: {}", sessionId);
                            Map<String, Object> sessionDetails = wasenderQRService.getSessionDetails(sessionId, apiKey);
                            
                            if (sessionDetails != null && Boolean.TRUE.equals(sessionDetails.get("success"))) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> data = (Map<String, Object>) sessionDetails.get("data");
                                if (data != null) {
                                    // Log all available keys in the response for debugging
                                    log.debug("Session details response data keys: {}", data.keySet());
                                    
                                    // Look for API key in the response - it might be in different fields
                                    String sessionApiKey = null;
                                    String foundInField = null;
                                    
                                    if (data.get("api_key") != null) {
                                        sessionApiKey = data.get("api_key").toString();
                                        foundInField = "api_key";
                                    } else if (data.get("apiKey") != null) {
                                        sessionApiKey = data.get("apiKey").toString();
                                        foundInField = "apiKey";
                                    } else if (data.get("token") != null) {
                                        sessionApiKey = data.get("token").toString();
                                        foundInField = "token";
                                    }
                                    
                                    if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                                        try {
                                            whatsAppSessionService.updateSessionApiKey(sessionId, sessionApiKey, session);
                                            log.info("Session API key successfully fetched from field '{}' and saved for session: {} (ID: {})", 
                                                    foundInField, actualSessionName, sessionId);
                                            log.debug("API key prefix: {}...", 
                                                    sessionApiKey.length() > 10 ? sessionApiKey.substring(0, 10) : sessionApiKey);
                                        } catch (Exception updateEx) {
                                            log.error("Failed to update session API key in database for session: {} (ID: {}). Error: {}", 
                                                    actualSessionName, sessionId, updateEx.getMessage(), updateEx);
                                        }
                                    } else {
                                        log.warn("Session details fetched but API key not found in response. Available fields: {} for session: {}", 
                                                data.keySet(), actualSessionName);
                                        if (log.isDebugEnabled()) {
                                            log.debug("Full session details data: {}", data);
                                        }
                                    }
                                } else {
                                    log.warn("Session details response data is null for session ID: {}", sessionId);
                                }
                            } else {
                                log.warn("Failed to fetch session details for session ID: {}. Response: {}", 
                                        sessionId, sessionDetails != null ? sessionDetails.get("error") : "null");
                            }
                        } catch (Exception fetchEx) {
                            log.error("Error fetching session details to get API key for session: {}", actualSessionName, fetchEx);
                            // Don't fail the request, just log the error
                        }
                    } else {
                        log.warn("Cannot fetch session API key: sessionId is null for session: {}", actualSessionName);
                    }
                } else {
                    log.debug("Session API key already exists for session: {} (ID: {})", actualSessionName, sessionId);
                }
            } catch (Exception e) {
                log.error("Error saving session to database", e);
                // Don't fail the request, just log the error
            }
        }
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/wasender/api-key")
    public ResponseEntity<?> saveWasenderApiKey(@RequestBody WasenderApiKeyRequest request) {
        try {
            wasenderConfigService.saveApiKey(request.getWasenderApiKey());
            WasenderApiKeyResponse response = new WasenderApiKeyResponse(true, "WASender API key saved successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to save API key: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/api/wasender/api-key/status")
    public ResponseEntity<WasenderApiKeyResponse> getWasenderApiKeyStatus() {
        boolean configured = wasenderConfigService.isConfigured();
        String message = configured 
            ? "WASender API key is configured" 
            : "WASender API key is not configured. Please configure it first.";
        WasenderApiKeyResponse response = new WasenderApiKeyResponse(configured, message);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/sendgrid/api-key")
    public ResponseEntity<?> saveSendGridApiKey(@RequestBody SendGridApiKeyRequest request) {
        try {
            sendGridConfigService.saveApiKey(
                request.getSendgridApiKey(),
                request.getEmailFromAddress(),
                request.getEmailFromName()
            );
            SendGridApiKeyResponse response = new SendGridApiKeyResponse(true, "SendGrid API key and email configuration saved successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to save API key: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/api/sendgrid/api-key/status")
    public ResponseEntity<SendGridApiKeyResponse> getSendGridApiKeyStatus() {
        boolean configured = sendGridConfigService.isConfigured();
        String message = configured 
            ? "SendGrid API key is configured" 
            : "SendGrid API key is not configured. Please configure it first.";
        SendGridApiKeyResponse response = new SendGridApiKeyResponse(configured, message);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/sites")
    public ResponseEntity<List<com.clapgrow.notification.api.entity.FrappeSite>> getAllSites() {
        List<com.clapgrow.notification.api.entity.FrappeSite> sites = siteService.getAllSites();
        return ResponseEntity.ok(sites);
    }

    @PostMapping("/api/sites/create")
    public ResponseEntity<?> createSite(@RequestBody SiteRegistrationRequest request, HttpSession session) {
        try {
            // Check if user has API key configured
            if (!userWasenderService.isConfigured(session)) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "WASender API key is not configured");
                errorResponse.put("errorCode", "API_KEY_REQUIRED");
                errorResponse.put("requiresApiKey", true);
                return ResponseEntity.status(428).body(errorResponse); // 428 Precondition Required
            }
            
            return ResponseEntity.ok(siteService.registerSite(request));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PutMapping("/api/sites/{siteId}/whatsapp-session")
    public ResponseEntity<?> updateSiteWhatsAppSession(
            @PathVariable(name = "siteId") String siteId,
            @RequestBody Map<String, String> request) {
        try {
            String sessionName = request.get("whatsappSessionName");
            if (sessionName == null || sessionName.trim().isEmpty()) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "WhatsApp session name is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            com.clapgrow.notification.api.entity.FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
            site.setWhatsappSessionName(sessionName.trim());
            site.setUpdatedBy("SYSTEM");
            siteService.updateSite(site);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "WhatsApp session updated successfully");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to update session: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/api/whatsapp/sessions")
    public ResponseEntity<Map<String, Object>> getAllSessions(HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.getAllSessions(apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/whatsapp/session/{sessionIdentifier}")
    public ResponseEntity<Map<String, Object>> getSessionDetails(
            @PathVariable(name = "sessionIdentifier") String sessionIdentifier,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.getSessionDetails(sessionIdentifier, apiKey);
        
        // Update session_api_key in database if found in response
        if (result != null && Boolean.TRUE.equals(result.get("success"))) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                if (data != null) {
                    // Extract session ID and name from response
                    String sessionId = data.get("id") != null ? data.get("id").toString() : sessionIdentifier;
                    String sessionName = data.get("name") != null ? data.get("name").toString() : null;
                    
                    // Look for API key in the response - it might be in different fields
                    String sessionApiKey = null;
                    String foundInField = null;
                    
                    if (data.get("api_key") != null) {
                        sessionApiKey = data.get("api_key").toString();
                        foundInField = "api_key";
                    } else if (data.get("apiKey") != null) {
                        sessionApiKey = data.get("apiKey").toString();
                        foundInField = "apiKey";
                    } else if (data.get("token") != null) {
                        sessionApiKey = data.get("token").toString();
                        foundInField = "token";
                    }
                    
                    // Update database if API key is found
                    if (sessionApiKey != null && !sessionApiKey.trim().isEmpty()) {
                        // Try to update by session ID first, then by session name
                        String identifierToUse = sessionId != null && !sessionId.trim().isEmpty() 
                            ? sessionId 
                            : (sessionName != null ? sessionName : sessionIdentifier);
                        
                        try {
                            whatsAppSessionService.updateSessionApiKey(identifierToUse, sessionApiKey, session);
                            log.info("Updated session_api_key in database from getSessionDetails endpoint for session: {} (ID: {})", 
                                    sessionName != null ? sessionName : identifierToUse, sessionId);
                            log.debug("API key found in field '{}' and updated in database", foundInField);
                        } catch (Exception updateEx) {
                            log.error("Failed to update session_api_key in database for session: {} (ID: {}). Error: {}", 
                                    identifierToUse, sessionId, updateEx.getMessage(), updateEx);
                        }
                    } else {
                        log.warn("Session details fetched but API key not found in response for session: {}. Available fields: {}", 
                                sessionIdentifier, data.keySet());
                        // Log the actual response data for debugging
                        if (log.isDebugEnabled()) {
                            log.debug("Full session details data: {}", data);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error updating session_api_key in database from getSessionDetails endpoint: {}", e.getMessage());
                // Don't fail the request, just log the warning
            }
        }
        
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/whatsapp/session/{sessionIdentifier}")
    public ResponseEntity<Map<String, Object>> deleteSession(
            @PathVariable(name = "sessionIdentifier") String sessionIdentifier,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.deleteSession(sessionIdentifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/whatsapp/session/{sessionIdentifier}/reconnect")
    public ResponseEntity<Map<String, Object>> reconnectSession(
            @PathVariable(name = "sessionIdentifier") String sessionIdentifier,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.connectSession(sessionIdentifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/api/whatsapp/session/{sessionIdentifier}")
    public ResponseEntity<Map<String, Object>> updateSession(
            @PathVariable(name = "sessionIdentifier") String sessionIdentifier,
            @RequestBody Map<String, Object> updateData,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.updateSession(sessionIdentifier, apiKey, updateData);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/whatsapp/session/{sessionIdentifier}/disconnect")
    public ResponseEntity<Map<String, Object>> disconnectSession(
            @PathVariable(name = "sessionIdentifier") String sessionIdentifier,
            HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.disconnectSession(sessionIdentifier, apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/whatsapp/status")
    public ResponseEntity<Map<String, Object>> getSessionStatus(HttpSession session) {
        // Get API key from user session
        String apiKey = userWasenderService.getApiKeyFromSession(session)
            .orElse(null);
        
        if (apiKey == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "WASender API key is not configured");
            error.put("errorCode", "API_KEY_REQUIRED");
            error.put("requiresApiKey", true);
            return ResponseEntity.status(428).body(error); // 428 Precondition Required
        }
        
        Map<String, Object> result = wasenderQRService.getSessionStatus(apiKey);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/whatsapp/user-sessions")
    public ResponseEntity<List<Map<String, Object>>> getUserSessions(HttpSession session) {
        try {
            // First, sync sessions from WASender API to ensure we have the latest
            String apiKey = userWasenderService.getApiKeyFromSession(session).orElse(null);
            if (apiKey != null) {
                try {
                    Map<String, Object> wasenderSessions = wasenderQRService.getAllSessions(apiKey);
                    if (Boolean.TRUE.equals(wasenderSessions.get("success")) && wasenderSessions.get("data") != null) {
                        whatsAppSessionService.syncSessionsFromWasender(wasenderSessions, session);
                    }
                } catch (Exception syncEx) {
                    log.warn("Could not sync sessions from WASender API: {}", syncEx.getMessage());
                }
            }
            
            // Get sessions from database
            List<com.clapgrow.notification.api.entity.WhatsAppSession> sessions = 
                whatsAppSessionService.getUserSessions(session);
            
            List<Map<String, Object>> result = sessions.stream()
                .map(s -> {
                    Map<String, Object> sessionMap = new HashMap<>();
                    sessionMap.put("id", s.getId().toString());
                    sessionMap.put("sessionId", s.getSessionId());
                    sessionMap.put("sessionName", s.getSessionName());
                    sessionMap.put("phoneNumber", s.getPhoneNumber());
                    sessionMap.put("status", s.getStatus());
                    sessionMap.put("hasApiKey", s.getSessionApiKey() != null && !s.getSessionApiKey().trim().isEmpty());
                    return sessionMap;
                })
                .toList();
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting user sessions", e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    @PostMapping("/api/whatsapp/session/{sessionIdentifier}/update-status")
    public ResponseEntity<Map<String, Object>> updateSessionStatus(
            @PathVariable(name = "sessionIdentifier") String sessionIdentifier,
            HttpSession session) {
        try {
            whatsAppSessionService.updateSessionOnConnect(sessionIdentifier, session);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Session status updated");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error updating session status", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

