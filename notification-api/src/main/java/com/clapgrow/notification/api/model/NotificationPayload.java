package com.clapgrow.notification.api.model;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationPayload {
    private String messageId;
    private String siteId;
    private String channel;
    private String recipient;
    private String subject;
    private String body;
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    private String caption;
    private String fromEmail;
    private String fromName;
    private Boolean isHtml;
    private String whatsappSessionName;
    private String sendgridApiKey;
    private Map<String, String> metadata;
}

