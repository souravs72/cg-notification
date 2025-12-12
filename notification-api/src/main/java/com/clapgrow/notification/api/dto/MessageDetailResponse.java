package com.clapgrow.notification.api.dto;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDetailResponse {
    private String messageId;
    private String siteName;
    private NotificationChannel channel;
    private DeliveryStatus status;
    private String recipient;
    private String subject;
    private String body;
    private String errorMessage;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
}

