package com.clapgrow.notification.email.repository;

import com.clapgrow.notification.email.entity.FrappeSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FrappeSite entity in email-worker module.
 * 
 * ⚠️ READ-ONLY OPERATIONS: This repository provides ONLY read operations for credential resolution.
 * All write operations (save, delete) are handled by notification-api module.
 * 
 * Usage: Used by SendGridService to resolve sendgridApiKey from siteId for email sending.
 */
@Repository
public interface FrappeSiteRepository extends JpaRepository<FrappeSite, UUID> {
    
    /**
     * Find Frappe site by ID (non-deleted only).
     * Used to resolve SendGrid API key from site ID.
     * 
     * ⚠️ READ-ONLY: This method only reads from the database. No writes or updates.
     */
    Optional<FrappeSite> findByIdAndIsDeletedFalse(UUID id);
}


