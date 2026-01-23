package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.annotation.AdminApi;
import com.clapgrow.notification.api.annotation.RequireAdminAuth;
import com.clapgrow.notification.api.dto.ApiResponse;
import com.clapgrow.notification.api.dto.BulkNotificationRequest;
import com.clapgrow.notification.api.dto.BulkScheduledNotificationRequest;
import com.clapgrow.notification.api.dto.NotificationRequest;
import com.clapgrow.notification.api.dto.NotificationResponse;
import com.clapgrow.notification.api.dto.ScheduledNotificationRequest;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.service.NotificationService;
import com.clapgrow.notification.api.service.ScheduledMessageService;
import com.clapgrow.notification.api.service.SiteService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/admin/campaigns")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Campaigns", description = "Administrative API endpoints for sending and scheduling campaign messages")
public class AdminCampaignController {
    
    private final NotificationService notificationService;
    private final ScheduledMessageService scheduledMessageService;
    private final SiteService siteService;

    @GetMapping
    public String campaignsPage(Model model) {
        List<FrappeSite> sites = siteService.getAllSites();
        model.addAttribute("sites", sites);
        return "admin/campaigns";
    }

    @PostMapping("/api/send")
    @AdminApi
    @RequireAdminAuth
    @Operation(
            summary = "Send a campaign message",
            description = "Sends a notification message from the admin interface. Supports both session-based and API key authentication."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendMessage(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID (optional for WhatsApp messages)")
            @RequestParam(name = "siteId", required = false) String siteId,
            @Valid @RequestBody NotificationRequest request,
            HttpSession session) {
        
        FrappeSite site = null;
        if (siteId != null && !siteId.trim().isEmpty()) {
            try {
                UUID siteUuid = UUID.fromString(siteId);
                site = siteService.getSiteById(siteUuid);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid site ID format or site not found: {}", siteId);
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid site ID: " + siteId));
            }
        }
        
        // If no site provided, use user's session info for WhatsApp messages
        // Let GlobalExceptionHandler handle any exceptions
        NotificationResponse response = notificationService.sendNotification(request, site, session);
        
        // 202 Accepted for async operations (message queued, not yet sent)
        return ResponseEntity.status(202)
            .body(ApiResponse.success(response));
    }

    @PostMapping("/api/send/bulk")
    @AdminApi
    @RequireAdminAuth
    @Operation(
            summary = "Send bulk campaign messages",
            description = "Sends multiple notification messages in a single request from the admin interface."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages sent successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<ApiResponse<BulkSendResponse>> sendBulkMessages(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID", required = true)
            @RequestParam(name = "siteId") String siteId,
            @Valid @RequestBody BulkNotificationRequest request,
            HttpSession session) {
        
        FrappeSite site;
        try {
            site = siteService.getSiteById(UUID.fromString(siteId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid site ID format or site not found: {}", siteId);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid site ID: " + siteId));
        }
        
        // Let GlobalExceptionHandler handle any exceptions
        List<NotificationResponse> responses = notificationService.sendBulkNotifications(request, site, session);
        
        BulkSendResponse result = new BulkSendResponse(
            request.getNotifications().size(),
            responses.size(),
            responses
        );
        
        // 202 Accepted for async operations (messages queued, not yet sent)
        return ResponseEntity.status(202)
            .body(ApiResponse.success(result));
    }

    @PostMapping("/api/schedule")
    @AdminApi
    @RequireAdminAuth
    @Operation(
            summary = "Schedule a campaign message",
            description = "Schedules a notification message for future delivery from the admin interface."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message scheduled successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<ApiResponse<NotificationResponse>> scheduleMessage(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID", required = true)
            @RequestParam(name = "siteId") String siteId,
            @Valid @RequestBody ScheduledNotificationRequest request,
            HttpSession session) {
        
        FrappeSite site;
        try {
            site = siteService.getSiteById(UUID.fromString(siteId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid site ID format or site not found: {}", siteId);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid site ID: " + siteId));
        }
        
        // Let GlobalExceptionHandler handle any exceptions
        NotificationResponse response = scheduledMessageService.scheduleNotification(request, site);
        
        // 201 Created for scheduled messages (resource created)
        return ResponseEntity.status(201)
            .body(ApiResponse.success(response));
    }

    @PostMapping("/api/schedule/bulk")
    @AdminApi
    @RequireAdminAuth
    @Operation(
            summary = "Schedule bulk campaign messages",
            description = "Schedules multiple notification messages for future delivery from the admin interface."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages scheduled successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @SecurityRequirement(name = "AdminKey")
    public ResponseEntity<ApiResponse<BulkScheduleResponse>> scheduleBulkMessages(
            @Parameter(description = "Admin API key for authentication (optional if using session)")
            @RequestHeader(name = "X-Admin-Key", required = false) String adminKey,
            @Parameter(description = "Site ID", required = true)
            @RequestParam(name = "siteId") String siteId,
            @Valid @RequestBody BulkScheduledNotificationRequest request,
            HttpSession session) {
        
        FrappeSite site;
        try {
            site = siteService.getSiteById(UUID.fromString(siteId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid site ID format or site not found: {}", siteId);
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Invalid site ID: " + siteId));
        }
        
        // Let GlobalExceptionHandler handle any exceptions
        List<NotificationResponse> responses = scheduledMessageService.scheduleBulkNotifications(
            request.getNotifications(), site
        );
        
        BulkScheduleResponse result = new BulkScheduleResponse(
            request.getNotifications().size(),
            responses.size(),
            responses
        );
        
        // 201 Created for scheduled messages (resources created)
        return ResponseEntity.status(201)
            .body(ApiResponse.success(result));
    }
    
    /**
     * Response DTO for bulk send operations.
     */
    public record BulkSendResponse(
        int totalRequested,
        int totalAccepted,
        List<NotificationResponse> results
    ) {}
    
    /**
     * Response DTO for bulk schedule operations.
     */
    public record BulkScheduleResponse(
        int totalRequested,
        int totalScheduled,
        List<NotificationResponse> results
    ) {}
}
