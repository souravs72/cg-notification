package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardResponse {
    private Long totalSites;
    private Long totalMessagesSent;
    private Long totalMessagesSuccess;
    private Long totalMessagesFailed;
    private Double overallSuccessRate;
    private List<AdminSiteMetrics> siteMetrics;

    /** Lightweight placeholder for fast initial render; JS fetches real data via /admin/api/metrics */
    public static AdminDashboardResponse empty() {
        return new AdminDashboardResponse(0L, 0L, 0L, 0L, 0.0, List.of());
    }
}

