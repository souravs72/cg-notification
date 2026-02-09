package com.clapgrow.notification.api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Abstract base class for message log services (Email, WhatsApp, etc.).
 * 
 * Provides common functionality for updating message status and querying retry counts.
 * This reduces duplication and ensures consistent behavior across all channels.
 * 
 * ⚠️ RETRY COUNT OWNERSHIP: Only MessagingRetryService should mutate retry_count.
 * Consumers should only set FAILED status, never increment retry counters.
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AbstractMessageLogService {
    
    protected final JdbcTemplate jdbcTemplate;

    /**
     * Get the SQL cast syntax for delivery_status enum.
     * Different databases may use different syntax, so this allows subclasses to override.
     * 
     * @return SQL cast syntax (e.g., "?::delivery_status" for PostgreSQL or "CAST(? AS delivery_status)")
     */
    protected String getStatusCastSyntax() {
        return "?::delivery_status";  // PostgreSQL syntax (default)
    }

    /**
     * Update message status.
     * 
     * Sets failure_type to CONSUMER when status is FAILED (consumer-side failures).
     * Clears failure_type for non-failure statuses.
     * 
     * @param messageId Message ID
     * @param status Status to set
     * @param errorMessage Error message (if any)
     */
    @Transactional
    public void updateStatus(String messageId, String status, String errorMessage) {
        try {
            String sql = String.format("""
                UPDATE message_logs 
                SET status = %s, 
                    error_message = ?,
                    updated_at = ?,
                    sent_at = CASE WHEN ? = 'SENT' THEN ? ELSE sent_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN ? ELSE delivered_at END,
                    failure_type = CASE WHEN ? = 'FAILED' THEN 'CONSUMER'::failure_type ELSE NULL END
                WHERE message_id = ?
                """, getStatusCastSyntax());
            
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
     * Get retry count for a message (read-only).
     * 
     * ⚠️ RETRY COUNT OWNERSHIP: Only MessagingRetryService should mutate retry_count.
     * This method is kept for read-only access if needed, but should not be used for mutations.
     * 
     * @param messageId Message ID
     * @return Current retry count (defaults to 0 if not found)
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
}

