package com.clapgrow.notification.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WasenderMessageRequest {
    private String to;
    private String text;
    private String imageUrl;
    private String videoUrl;
    private String documentUrl;
    private String fileName;
    private String caption;
    private String audioUrl;
    private Location location;
    private String replyTo; // Message ID to reply to
    private String whatsappSession; // Session name to route message to specific WhatsApp number
    
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Location {
        private Double latitude;
        private Double longitude;
        private String name;
        private String address;
    }
}

