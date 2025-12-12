package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyMetricsResponse {
    private String siteId;
    private List<DailyMetric> dailyMetrics;
}

