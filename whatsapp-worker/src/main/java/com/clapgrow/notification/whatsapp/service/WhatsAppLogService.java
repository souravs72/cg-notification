package com.clapgrow.notification.whatsapp.service;

import com.clapgrow.notification.whatsapp.enums.DeliveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WhatsApp message log service.
 * 
 * ⚠️ CRITICAL: This service duplicates code from EmailLogService and notification-api's
 * AbstractMessageLogService, but workers cannot share code from notification-api module
 * due to circular dependency risks.
 * 
 * Both services maintain the same logic for consistency. If you modify this service,
 * ensure EmailLogService and AbstractMessageLogService are updated accordingly.
 * 
 * DeliveryStatus enum is also duplicated - see DeliveryStatus.java for sync requirements.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppLogService {
    
    private final JdbcTemplate jdbcTemplate;

    /**
     * Update message status using enum instead of string.
     * 
     * @param messageId Message ID
     * @param status Delivery status enum
     * @param errorMessage Error message (nullable)
     */
    @Transactional
    public void updateStatus(String messageId, DeliveryStatus status, String errorMessage) {
        updateStatus(messageId, status.getValue(), errorMessage);
    }
    
    /**
     * Update message status (legacy string-based method for backward compatibility).
     * 
     * @param messageId Message ID
     * @param status Status string (must match DeliveryStatus enum values)
     * @param errorMessage Error message (nullable)
     */
    @Transactional
    public void updateStatus(String messageId, String status, String errorMessage) {
        try {
            String sql = """
                UPDATE message_logs 
                SET status = ?::delivery_status, 
                    error_message = ?,
                    updated_at = ?,
                    sent_at = CASE WHEN ? = 'SENT' THEN ? ELSE sent_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN ? ELSE delivered_at END,
                    failure_type = CASE WHEN ? = 'FAILED' THEN 'CONSUMER'::failure_type ELSE NULL END
                WHERE message_id = ?
                """;
            
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(sql, 
                status, errorMessage, now,
                status, now,
                status, now,
                status,
                messageId
            );
            
            log.debug("Updated message log {} status to {}", messageId, status);
        } catch (Exception e) {
            log.error("Failed to update message log status for {}", messageId, e);
        }
    }

    /**
     * Get current status for a message (for idempotency: skip if already DELIVERED).
     */
    public Optional<String> getStatus(String messageId) {
        try {
            String sql = "SELECT status::text FROM message_logs WHERE message_id = ?";
            List<String> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), messageId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to get status for {}: {}", messageId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * ⚠️ RETRY COUNT OWNERSHIP: Only MessagingRetryService (API) should mutate retry_count.
     * Consumers should only set FAILED status, never increment retry counters.
     * This method is kept for read-only access if needed, but should not be used for mutations.
     */
    public int getRetryCount(String messageId) {
        try {
            String sql = "SELECT retry_count FROM message_logs WHERE message_id = ?";
            List<Integer> results = jdbcTemplate.query(sql, 
                (rs, rowNum) -> rs.getInt("retry_count"), 
                messageId);
            return results.isEmpty() ? 0 : results.get(0);
        } catch (Exception e) {
            log.warn("Failed to get retry count for {}, defaulting to 0: {}", messageId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get site_id for a message from message_logs table.
     * Used for tenant isolation verification before credential resolution.
     * 
     * @param messageId Message ID
     * @return Site ID (UUID) if found, empty Optional otherwise
     */
    public Optional<UUID> getSiteId(String messageId) {
        try {
            String sql = "SELECT site_id FROM message_logs WHERE message_id = ?";
            List<UUID> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Object siteIdObj = rs.getObject("site_id");
                    if (siteIdObj instanceof UUID) {
                        return (UUID) siteIdObj;
                    }
                    return null;
                },
                messageId);
            return results.isEmpty() || results.get(0) == null 
                ? Optional.empty() 
                : Optional.of(results.get(0));
        } catch (Exception e) {
            log.error("Failed to get site_id for message {}, defaulting to empty: {}", messageId, e.getMessage());
            return Optional.empty();
        }
    }
}
