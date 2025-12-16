package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "whatsapp_sessions", indexes = {
    @Index(name = "idx_whatsapp_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_whatsapp_sessions_session_id", columnList = "session_id"),
    @Index(name = "idx_whatsapp_sessions_status", columnList = "status"),
    @Index(name = "idx_whatsapp_sessions_active", columnList = "user_id, is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsAppSession extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId; // WASender session ID

    @Column(name = "session_name", nullable = false, length = 255)
    private String sessionName;

    @Column(name = "session_api_key", length = 500)
    private String sessionApiKey; // API key generated for this session when connected

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "status", nullable = false, length = 50)
    private String status = "PENDING"; // PENDING, NEED_SCAN, CONNECTING, CONNECTED, DISCONNECTED

    @Column(name = "account_protection")
    private Boolean accountProtection = true;

    @Column(name = "log_messages")
    private Boolean logMessages = true;

    @Column(name = "webhook_url", length = 1000)
    private String webhookUrl;

    @Column(name = "webhook_events", columnDefinition = "TEXT[]")
    private String[] webhookEvents;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;
}





