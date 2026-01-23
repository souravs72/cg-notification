package com.clapgrow.notification.whatsapp.repository;

import com.clapgrow.notification.whatsapp.entity.WhatsAppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for WhatsAppSession entity in whatsapp-worker module.
 * 
 * ⚠️ READ-ONLY OPERATIONS: This repository provides ONLY read operations for credential resolution.
 * All write operations (save, delete) are handled by notification-api module.
 * 
 * Usage: Used by WasenderService to resolve sessionApiKey from sessionName for message delivery.
 */
@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, UUID> {
    
    /**
     * Find WhatsApp session by session name (non-deleted only).
     * Used to resolve session API key from session name.
     * 
     * ⚠️ READ-ONLY: This method only reads from the database. No writes or updates.
     */
    Optional<WhatsAppSession> findFirstBySessionNameAndIsDeletedFalse(String sessionName);
}

