package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.*;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.service.NotificationService;
import com.clapgrow.notification.api.service.ScheduledMessageService;
import com.clapgrow.notification.api.service.SiteService;
import jakarta.validation.Valid;
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
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestParam String siteId,
            @Valid @RequestBody NotificationRequest request) {
        
        try {
            FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
            NotificationResponse response = notificationService.sendNotification(request, site);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("messageId", response.getMessageId());
            result.put("status", response.getStatus());
            result.put("message", response.getMessage());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/api/send/bulk")
    public ResponseEntity<Map<String, Object>> sendBulkMessages(
            @RequestParam String siteId,
            @Valid @RequestBody BulkNotificationRequest request) {
        
        try {
            FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
            List<NotificationResponse> responses = notificationService.sendBulkNotifications(request, site);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("totalRequested", request.getNotifications().size());
            result.put("totalAccepted", responses.size());
            result.put("results", responses);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/api/schedule")
    public ResponseEntity<Map<String, Object>> scheduleMessage(
            @RequestParam String siteId,
            @Valid @RequestBody ScheduledNotificationRequest request) {
        
        try {
            FrappeSite site = siteService.getSiteById(java.util.UUID.fromString(siteId));
            NotificationResponse response = scheduledMessageService.scheduleNotification(request, site);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("messageId", response.getMessageId());
            result.put("status", response.getStatus());
            result.put("message", response.getMessage());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/api/schedule/bulk")
    public ResponseEntity<Map<String, Object>> scheduleBulkMessages(
            @RequestParam String siteId,
            @Valid @RequestBody BulkScheduledNotificationRequest request) {
        
        try {
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
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}

