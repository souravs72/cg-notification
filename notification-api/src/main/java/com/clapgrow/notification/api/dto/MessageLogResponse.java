package com.clapgrow.notification.api.dto;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageLogResponse {
    private UUID id;
    private String messageId;
    private UUID siteId;
    private String siteName;
    private NotificationChannel channel;
    private DeliveryStatus status;
    private String recipient;
    private String subject;
    private String body;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // WhatsApp specific fields
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    private String caption;
    
    // Email specific fields
    private String fromEmail;
    private String fromName;
    private Boolean isHtml;
    
    // Metadata
    private Map<String, String> metadata;
}

