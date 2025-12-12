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
}

