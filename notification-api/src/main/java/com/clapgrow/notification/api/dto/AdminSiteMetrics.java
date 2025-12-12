package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSiteMetrics {
    private UUID siteId;
    private String siteName;
    private Long totalSent;
    private Long totalSuccess;
    private Long totalFailed;
    private Double successRate;
    private Map<String, ChannelMetrics> channelMetrics;
}

