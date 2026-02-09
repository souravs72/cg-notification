package com.clapgrow.notification.email.service;

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
 * Email message log service.
 * 
 * ⚠️ NOTE: This duplicates code from WhatsAppLogService, but workers can't share code from notification-api module. Both services maintain the same logic for consistency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailLogService {
    
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void updateStatus(String messageId, String status, String errorMessage) {
        try {
            String sql = """
                UPDATE message_logs 
                SET status = CAST(? AS delivery_status), 
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
     * ⚠️ RETRY COUNT OWNERSHIP: Only KafkaRetryService should mutate retry_count.
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
     * Result of looking up a message in message_logs for tenant isolation.
     * Distinguishes "row not found" from "row found with site_id = null" (dashboard messages).
     */
    public record MessageSiteLookup(boolean found, UUID siteId) {}

    /**
     * Get message_logs row and site_id for tenant isolation.
     * Dashboard messages have site_id = null; API messages have site_id set.
     *
     * @param messageId Message ID
     * @return MessageSiteLookup: found=false when row does not exist; found=true, siteId=null when row exists with no site; found=true, siteId=uuid when row has that site
     */
    public MessageSiteLookup getMessageSiteLookup(String messageId) {
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
            if (results.isEmpty()) {
                return new MessageSiteLookup(false, null);
            }
            return new MessageSiteLookup(true, results.get(0));
        } catch (Exception e) {
            log.error("Failed to get site_id for message {}, treating as not found: {}", messageId, e.getMessage());
            return new MessageSiteLookup(false, null);
        }
    }

    /**
     * Get site_id for a message from message_logs table.
     * Used for tenant isolation verification before credential resolution.
     * Prefer {@link #getMessageSiteLookup(String)} when you need to allow null site_id (dashboard messages).
     *
     * @param messageId Message ID
     * @return Site ID (UUID) if row exists and site_id is non-null; empty otherwise (row missing or site_id null)
     */
    public Optional<UUID> getSiteId(String messageId) {
        MessageSiteLookup lookup = getMessageSiteLookup(messageId);
        return lookup.found() && lookup.siteId() != null ? Optional.of(lookup.siteId()) : Optional.empty();
    }
}

