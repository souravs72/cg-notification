package com.clapgrow.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private String messageId;
    private String status;
    private String message;
    private String channel;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class BulkNotificationResponse {
    private Integer totalRequested;
    private Integer totalAccepted;
    private List<NotificationResponse> results;
}

