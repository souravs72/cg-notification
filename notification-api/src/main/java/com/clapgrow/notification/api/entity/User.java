package com.clapgrow.notification.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseAuditableEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "email", nullable = false, unique = true)
    private String email;
    
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    
    @Column(name = "wasender_api_key")
    private String wasenderApiKey;
    
    @Column(name = "subscription_type")
    private String subscriptionType;
    
    @Column(name = "subscription_status")
    private String subscriptionStatus;
    
    @Column(name = "sessions_allowed")
    private Integer sessionsAllowed;
    
    @Column(name = "sessions_used")
    private Integer sessionsUsed;
    
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}

