package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.DailyMetricsResponse;
import com.clapgrow.notification.api.dto.MetricsSummaryResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.service.MetricsService;
import com.clapgrow.notification.api.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {
    
    private final MetricsService metricsService;
    private final SiteService siteService;

    @GetMapping("/site/summary")
    public ResponseEntity<MetricsSummaryResponse> getSiteSummary(
            @RequestHeader("X-Site-Key") String apiKey) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        MetricsSummaryResponse response = metricsService.getSiteSummary(site.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/site/daily")
    public ResponseEntity<DailyMetricsResponse> getDailyMetrics(
            @RequestHeader("X-Site-Key") String apiKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        DailyMetricsResponse response = metricsService.getDailyMetrics(site.getId(), startDate, endDate);
        return ResponseEntity.ok(response);
    }
}

