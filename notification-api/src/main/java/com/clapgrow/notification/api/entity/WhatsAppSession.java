package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "whatsapp_sessions")
@Data
@EqualsAndHashCode(callSuper = true)
public class WhatsAppSession extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "session_name", nullable = false)
    private String sessionName;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "api_key")
    private String apiKey;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "session_api_key")
    private String sessionApiKey;
}

