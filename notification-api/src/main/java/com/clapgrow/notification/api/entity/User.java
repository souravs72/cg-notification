package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_email", columnList = "email"),
    @Index(name = "idx_user_active", columnList = "is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "wasender_api_key", length = 500)
    private String wasenderApiKey;

    @Column(name = "subscription_type", length = 50)
    private String subscriptionType = "FREE_TRIAL"; // FREE_TRIAL, PAID

    @Column(name = "subscription_status", length = 50)
    private String subscriptionStatus = "ACTIVE"; // ACTIVE, EXPIRED, CANCELLED

    @Column(name = "sessions_allowed")
    private Integer sessionsAllowed = 10;

    @Column(name = "sessions_used")
    private Integer sessionsUsed = 0;
}

