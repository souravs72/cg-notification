package com.clapgrow.notification.whatsapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Frappe site entity for whatsapp-worker module.
 * 
 * ⚠️ READ-ONLY PROJECTION: This entity is a read-only projection used ONLY for credential resolution.
 * 
 * Ownership and responsibilities:
 * - Schema ownership: notification-api module owns the frappe_sites table schema
 * - Write operations: All writes (create, update, delete) happen in notification-api module
 * - Lifecycle management: Site registration and updates are handled by notification-api
 * - Usage in whatsapp-worker: This projection is used ONLY to read whatsappSessionName for session lookup
 * 
 * ⚠️ DUPLICATED: This entity is duplicated from notification-api module
 * because workers cannot depend on notification-api module (circular dependency risk).
 * 
 * IMPORTANT: If you modify this entity, ensure FrappeSite in notification-api
 * is updated accordingly to maintain consistency.
 * 
 * ⚠️ DO NOT ADD: Write methods, lifecycle logic, or schema migration scripts here.
 * This is a read-only projection for credential resolution only.
 * 
 * ⚠️ TENANT INVARIANT: Each site has at most one active WhatsApp session used for message delivery.
 * The whatsappSessionName field references the session name, which is resolved to sessionApiKey
 * via WhatsAppSession lookup.
 */
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

