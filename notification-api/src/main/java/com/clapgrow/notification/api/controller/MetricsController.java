package com.clapgrow.notification.api.controller;

import com.clapgrow.notification.api.dto.DailyMetricsResponse;
import com.clapgrow.notification.api.dto.MetricsSummaryResponse;
import com.clapgrow.notification.api.entity.FrappeSite;
import com.clapgrow.notification.api.service.MetricsService;
import com.clapgrow.notification.api.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "API endpoints for retrieving notification metrics and statistics")
public class MetricsController {
    
    private final MetricsService metricsService;
    private final SiteService siteService;

    @GetMapping("/site/summary")
    @Operation(
            summary = "Get site metrics summary",
            description = "Retrieves a summary of metrics for the authenticated site including total messages, success rates, and channel-specific statistics."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = MetricsSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<MetricsSummaryResponse> getSiteSummary(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey) {
        
        FrappeSite site = siteService.validateApiKey(apiKey);
        MetricsSummaryResponse response = metricsService.getSiteSummary(site.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/site/daily")
    @Operation(
            summary = "Get daily metrics",
            description = "Retrieves daily metrics for the authenticated site within a specified date range. Defaults to last 30 days if no dates are provided."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Daily metrics retrieved successfully",
                    content = @Content(schema = @Schema(implementation = DailyMetricsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API key")
    })
    @SecurityRequirement(name = "SiteKey")
    public ResponseEntity<DailyMetricsResponse> getDailyMetrics(
            @Parameter(description = "Site API key for authentication", required = true)
            @RequestHeader(name = "X-Site-Key") String apiKey,
            @Parameter(description = "Start date for metrics (ISO format: YYYY-MM-DD). Defaults to 30 days ago.")
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date for metrics (ISO format: YYYY-MM-DD). Defaults to today.")
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
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

