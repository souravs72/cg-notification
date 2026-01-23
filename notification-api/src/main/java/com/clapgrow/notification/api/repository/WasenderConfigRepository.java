package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.WasenderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasenderConfigRepository extends JpaRepository<WasenderConfig, UUID> {
    
    /**
     * Find the active (non-deleted) WASender configuration
     * There should only be one active configuration at a time
     */
    Optional<WasenderConfig> findByIsDeletedFalse();
    
    /**
     * Check if an active configuration exists
     */
    boolean existsByIsDeletedFalse();
}