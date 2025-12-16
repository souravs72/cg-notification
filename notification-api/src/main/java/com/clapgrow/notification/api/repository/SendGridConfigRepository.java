package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.SendGridConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SendGridConfigRepository extends JpaRepository<SendGridConfig, UUID> {
    
    /**
     * Find the active (non-deleted) SendGrid configuration
     * There should only be one active configuration at a time
     */
    Optional<SendGridConfig> findByIsDeletedFalse();
    
    /**
     * Check if an active configuration exists
     */
    boolean existsByIsDeletedFalse();
}





