package com.clapgrow.notification.whatsapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppLogService {
    
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void updateStatus(String messageId, String status, String errorMessage) {
        try {
            String sql = """
                UPDATE message_logs 
                SET status = ?::delivery_status, 
                    error_message = ?,
                    updated_at = ?,
                    sent_at = CASE WHEN ? = 'SENT' THEN ? ELSE sent_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN ? ELSE delivered_at END
                WHERE message_id = ?
                """;
            
            LocalDateTime now = LocalDateTime.now();
            jdbcTemplate.update(sql, 
                status, errorMessage, now,
                status, now,
                status, now,
                messageId
            );
            
            log.debug("Updated message log {} status to {}", messageId, status);
        } catch (Exception e) {
            log.error("Failed to update message log status for {}", messageId, e);
        }
    }

    @Transactional
    public void incrementRetryCount(String messageId) {
        try {
            String sql = "UPDATE message_logs SET retry_count = retry_count + 1 WHERE message_id = ?";
            jdbcTemplate.update(sql, messageId);
        } catch (Exception e) {
            log.error("Failed to increment retry count for {}", messageId, e);
        }
    }

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

