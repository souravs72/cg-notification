package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSummaryResponse {
    private String siteId;
    private Long totalSent;
    private Long totalSuccess;
    private Long totalFailed;
    private Map<String, ChannelMetrics> channelMetrics;
}

