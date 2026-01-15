package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.WhatsAppSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, UUID> {
    Optional<WhatsAppSession> findByUserIdAndSessionNameAndIsDeletedFalse(UUID userId, String sessionName);
    Optional<WhatsAppSession> findByUserIdAndSessionIdAndIsDeletedFalse(UUID userId, String sessionId);
    List<WhatsAppSession> findByUserIdAndIsDeletedFalseOrderByCreatedAtDesc(UUID userId);
    Optional<WhatsAppSession> findByUserIdAndSessionNameAndIsDeletedFalseAndStatus(UUID userId, String sessionName, String status);
    List<WhatsAppSession> findByUserIdAndIsDeletedFalseAndStatusOrderByCreatedAtDesc(UUID userId, String status);
}










