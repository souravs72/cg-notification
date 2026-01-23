package com.clapgrow.notification.whatsapp.repository;

import com.clapgrow.notification.whatsapp.entity.FrappeSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for FrappeSite entity in whatsapp-worker module.
 * 
 * ⚠️ READ-ONLY OPERATIONS: This repository provides ONLY read operations for credential resolution.
 * All write operations (save, delete) are handled by notification-api module.
 * 
 * Usage: Used by WasenderService to resolve whatsappSessionName from siteId for session lookup.
 */
@Repository
public interface FrappeSiteRepository extends JpaRepository<FrappeSite, UUID> {
    
    /**
     * Find Frappe site by ID (non-deleted only).
     * Used to resolve WhatsApp session name from site ID.
     * 
     * ⚠️ READ-ONLY: This method only reads from the database. No writes or updates.
     */
    Optional<FrappeSite> findByIdAndIsDeletedFalse(UUID id);
}

