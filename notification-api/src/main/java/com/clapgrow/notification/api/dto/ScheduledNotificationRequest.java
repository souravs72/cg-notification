package com.clapgrow.notification.api.dto;

import com.clapgrow.notification.api.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ScheduledNotificationRequest {
    @NotNull(message = "Channel is required")
    private NotificationChannel channel;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    private String subject;

    @NotBlank(message = "Body is required")
    private String body;

    @NotNull(message = "Scheduled time is required")
    private LocalDateTime scheduledAt;
    
    // Custom validation method
    public boolean isValidScheduledTime() {
        return scheduledAt != null && scheduledAt.isAfter(LocalDateTime.now());
    }

    // WhatsApp specific fields
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    private String caption;
    /**
     * WASender API key for WhatsApp messages.
     * Required for WhatsApp channel when no site is provided.
     * ⚠️ SECURITY: This field is sensitive and should never be logged or exposed in responses.
     */
    private String wasenderApiKey;

    // Email specific fields
    private String fromEmail;
    private String fromName;
    private Boolean isHtml = false;

    // Additional metadata
    private Map<String, String> metadata;
}

