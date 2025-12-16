package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.*;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.service.AdminAuthService;
import com.clapgrow.notification.api.service.NotificationService;
import com.clapgrow.notification.api.service.ScheduledMessageService;
import com.clapgrow.notification.api.service.SiteService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/campaigns")
@RequiredArgsConstructor
@Tag(name = "Admin Campaigns", description = "Administrative API endpoints for sending and scheduling campaign messages")
public class AdminCampaignController {
    
    private final NotificationService notificationService;
    private final ScheduledMessageService scheduledMessageService;
    private final SiteService siteService;
    private final AdminAuthService adminAuthService;

    @GetMapping
    public String campaignsPage(Model model) {
        List<FrappeSite> sites = siteService.getAllSites();
        model.addAttribute("sites", sites);
        return "admin/campaigns";
    }

    @PostMapping("/api/send")
    @Operation(
            summary = "Send a campaign message",
            description = "Sends a notification message from the admin interface. Supports both session-based and API key authentication."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID (optional for WhatsApp messages)")
            @RequestParam(name = "siteId", required = false) String siteId,
            @Valid @RequestBody NotificationRequest request,
            HttpSession session) {
        
        // For authenticated dashboard users, session authentication is sufficient
        // Admin API key is only required for external API calls
        if (session.getAttribute("userId") == null) {
            if (adminKey == null || adminKey.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Authentication required");
                return ResponseEntity.status(401).body(error);
            }
            adminAuthService.validateAdminKey(adminKey);
        }
        
        FrappeSite site = null;
        if (siteId != null && !siteId.trim().isEmpty()) {
            try {
                site = siteService.getSiteById(java.util.UUID.fromString(siteId));
            } catch (Exception e) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Invalid site ID: " + e.getMessage());
                return ResponseEntity.badRequest().body(error);
            }
        }
        
        // If no site provided, use user's session info for WhatsApp messages
        NotificationResponse response = notificationService.sendNotification(request, site, session);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("messageId", response.getMessageId());
        result.put("status", response.getStatus());
        result.put("message", response.getMessage());
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/send/bulk")
    @Operation(
            summary = "Send bulk campaign messages",
            description = "Sends multiple notification messages in a single request from the admin interface."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<Map<String, Object>> sendBulkMessages(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID", required = true)
            @RequestParam(name = "siteId") String siteId,
            @Valid @RequestBody BulkNotificationRequest request,
            HttpSession session) {
        
        // For authenticated dashboard users, session authentication is sufficient
        if (session.getAttribute("userId") == null) {
            if (adminKey == null || adminKey.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Authentication required");
                return ResponseEntity.status(401).body(error);
            }
            adminAuthService.validateAdminKey(adminKey);
        }
        FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
        List<NotificationResponse> responses = notificationService.sendBulkNotifications(request, site);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalRequested", request.getNotifications().size());
        result.put("totalAccepted", responses.size());
        result.put("results", responses);
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/schedule")
    @Operation(
            summary = "Schedule a campaign message",
            description = "Schedules a notification message for future delivery from the admin interface."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message scheduled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<Map<String, Object>> scheduleMessage(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID", required = true)
            @RequestParam(name = "siteId") String siteId,
            @Valid @RequestBody ScheduledNotificationRequest request,
            HttpSession session) {
        
        // For authenticated dashboard users, session authentication is sufficient
        if (session.getAttribute("userId") == null) {
            if (adminKey == null || adminKey.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Authentication required");
                return ResponseEntity.status(401).body(error);
            }
            adminAuthService.validateAdminKey(adminKey);
        }
        FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
        NotificationResponse response = scheduledMessageService.scheduleNotification(request, site);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("messageId", response.getMessageId());
        result.put("status", response.getStatus());
        result.put("message", response.getMessage());
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/schedule/bulk")
    @Operation(
            summary = "Schedule bulk campaign messages",
            description = "Schedules multiple notification messages for future delivery from the admin interface."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Messages scheduled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<Map<String, Object>> scheduleBulkMessages(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID", required = true)
            @RequestParam(name = "siteId") String siteId,
            @Valid @RequestBody BulkScheduledNotificationRequest request,
            HttpSession session) {
        
        // For authenticated dashboard users, session authentication is sufficient
        if (session.getAttribute("userId") == null) {
            if (adminKey == null || adminKey.trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("error", "Authentication required");
                return ResponseEntity.status(401).body(error);
            }
            adminAuthService.validateAdminKey(adminKey);
        }
        FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
        List<NotificationResponse> responses = scheduledMessageService.scheduleBulkNotifications(
            request.getNotifications(), site
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalRequested", request.getNotifications().size());
        result.put("totalScheduled", responses.size());
        result.put("results", responses);
        
        return ResponseEntity.ok(result);
    }
}

