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
public class NotificationController {
    
    private final NotificationService notificationService;
    private final ScheduledMessageService scheduledMessageService;
    private final SiteService siteService;

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendNotification(
            @RequestHeader("X-Site-Key") String apiKey,
            @Valid @RequestBody NotificationRequest request) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        NotificationResponse response = notificationService.sendNotification(request, site);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/send/bulk")
    public ResponseEntity<Map<String, Object>> sendBulkNotifications(
            @RequestHeader("X-Site-Key") String apiKey,
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
    public ResponseEntity<NotificationResponse> scheduleNotification(
            @RequestHeader("X-Site-Key") String apiKey,
            @Valid @RequestBody ScheduledNotificationRequest request) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        NotificationResponse response = scheduledMessageService.scheduleNotification(request, site);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/schedule/bulk")
    public ResponseEntity<Map<String, Object>> scheduleBulkNotifications(
            @RequestHeader("X-Site-Key") String apiKey,
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

