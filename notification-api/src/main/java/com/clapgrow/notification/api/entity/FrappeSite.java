package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "frappe_sites", indexes = {
    @Index(name = "idx_site_key", columnList = "api_key_hash"),
    @Index(name = "idx_site_name", columnList = "site_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FrappeSite extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "site_name", nullable = false, unique = true, length = 255)
    private String siteName;

    @Column(name = "api_key", nullable = false, unique = true, length = 128)
    private String apiKey;

    @Column(name = "api_key_hash", nullable = false, length = 255)
    private String apiKeyHash;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "whatsapp_session_name", length = 255)
    private String whatsappSessionName;

    @Column(name = "email_from_address", length = 255)
    private String emailFromAddress;

    @Column(name = "email_from_name", length = 255)
    private String emailFromName;

    @Column(name = "sendgrid_api_key", length = 255)
    private String sendgridApiKey;
}

