package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.BulkNotificationRequest;
import com.clapgrow.notification.api.dto.BulkScheduledNotificationRequest;
import com.clapgrow.notification.api.dto.NotificationRequest;
import com.clapgrow.notification.api.dto.NotificationResponse;
import com.clapgrow.notification.api.dto.ScheduledNotificationRequest;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.service.NotificationService;
import com.clapgrow.notification.api.service.ScheduledMessageService;
import com.clapgrow.notification.api.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "API endpoints for sending and scheduling notifications")
public class NotificationController {
    
    private final NotificationService notificationService;
    private final ScheduledMessageService scheduledMessageService;
    private final SiteService siteService;

    @PostMapping("/send")
    @Operation(
            summary = "Send a notification",
            description = "Sends a single notification via email or WhatsApp channel. The notification is queued for processing."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Notification accepted for processing",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<NotificationResponse> sendNotification(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Valid @RequestBody NotificationRequest request) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        NotificationResponse response = notificationService.sendNotification(request, site);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/send/bulk")
    @Operation(
            summary = "Send bulk notifications",
            description = "Sends multiple notifications in a single request. All notifications are queued for processing."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Notifications accepted for processing"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<Map<String, Object>> sendBulkNotifications(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Valid @RequestBody BulkNotificationRequest request) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        List<NotificationResponse> responses = notificationService.sendBulkNotifications(request, site);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalRequested", request.getNotifications().size());
        result.put("totalAccepted", responses.size());
        result.put("results", responses);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/schedule")
    @Operation(
            summary = "Schedule a notification",
            description = "Schedules a notification to be sent at a specific date and time."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Notification scheduled successfully",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<NotificationResponse> scheduleNotification(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Valid @RequestBody ScheduledNotificationRequest request) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        NotificationResponse response = scheduledMessageService.scheduleNotification(request, site);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/schedule/bulk")
    @Operation(
            summary = "Schedule bulk notifications",
            description = "Schedules multiple notifications to be sent at specific dates and times."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Notifications scheduled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<Map<String, Object>> scheduleBulkNotifications(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Valid @RequestBody BulkScheduledNotificationRequest request) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        List<NotificationResponse> responses = scheduledMessageService.scheduleBulkNotifications(
            request.getNotifications(), site
        );
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalRequested", request.getNotifications().size());
        result.put("totalScheduled", responses.size());
        result.put("results", responses);
        
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }
}

