package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMetrics {
    private String channel;
    private Long totalSent;
    private Long totalSuccess;
    private Long totalFailed;
}

