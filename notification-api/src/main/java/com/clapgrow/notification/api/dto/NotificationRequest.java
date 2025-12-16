package com.clapgrow.notification.api.dto;

import com.clapgrow.notification.api.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class NotificationRequest {
    @NotNull(message = "Channel is required")
    private NotificationChannel channel;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    private String subject;

    @NotBlank(message = "Body is required")
    private String body;

    // WhatsApp specific fields
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    private String caption;
    private String whatsappSessionName; // Specific WhatsApp session to use

    // Email specific fields
    private String fromEmail;
    private String fromName;
    private Boolean isHtml = false;

    // Additional metadata
    private Map<String, String> metadata;
}

