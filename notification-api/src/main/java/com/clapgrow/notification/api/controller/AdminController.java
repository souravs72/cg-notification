package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.AdminDashboardResponse;
import com.clapgrow.notification.api.dto.MessageDetailResponse;
import com.clapgrow.notification.api.dto.SiteRegistrationRequest;
import com.clapgrow.notification.api.service.AdminAuthService;
import com.clapgrow.notification.api.service.AdminService;
import com.clapgrow.notification.api.service.SiteService;
import com.clapgrow.notification.api.service.WasenderQRService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
public class AdminController {
    
    private final AdminService adminService;
    private final SiteService siteService;
    private final WasenderQRService wasenderQRService;
    private final AdminAuthService adminAuthService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, @Value("${admin.api-key:}") String adminApiKey) {
        AdminDashboardResponse metrics = adminService.getDashboardMetrics();
        model.addAttribute("metrics", metrics);
        model.addAttribute("adminApiKey", adminApiKey);
        return "admin/dashboard";
    }

    @GetMapping("/api/metrics")
    public ResponseEntity<AdminDashboardResponse> getMetrics() {
        AdminDashboardResponse response = adminService.getDashboardMetrics();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/messages/recent")
    public ResponseEntity<List<MessageDetailResponse>> getRecentMessages(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<MessageDetailResponse> messages = adminService.getRecentMessages(limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/api/messages/failed")
    public ResponseEntity<List<MessageDetailResponse>> getFailedMessages(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<MessageDetailResponse> messages = adminService.getFailedMessages(limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/api/messages/scheduled")
    public ResponseEntity<List<MessageDetailResponse>> getScheduledMessages(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<MessageDetailResponse> messages = adminService.getScheduledMessages(limit);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/api/sites/create")
    public ResponseEntity<?> createSite(@RequestBody SiteRegistrationRequest request) {
        try {
            return ResponseEntity.ok(siteService.registerSite(request));
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @DeleteMapping("/api/sites/{siteId}")
    public ResponseEntity<?> deleteSite(
            @RequestHeader("X-Admin-Key") String adminKey,
            @PathVariable String siteId) {
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
            @RequestParam String sessionName) {
        Map<String, Object> result = wasenderQRService.getQRCode(sessionName);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/whatsapp/session")
    public ResponseEntity<Map<String, Object>> createWhatsAppSession(
            @RequestBody Map<String, String> request) {
        String sessionName = request.get("sessionName");
        if (sessionName == null || sessionName.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Session name is required");
            return ResponseEntity.badRequest().body(error);
        }
        Map<String, Object> result = wasenderQRService.createSession(sessionName);
        return ResponseEntity.ok(result);
    }
}

