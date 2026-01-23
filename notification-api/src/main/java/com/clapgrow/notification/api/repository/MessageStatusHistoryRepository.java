package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.MessageStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageStatusHistoryRepository extends JpaRepository<MessageStatusHistory, UUID> {
    
    /**
     * Find all status history entries for a message, ordered by timestamp.
     */
    List<MessageStatusHistory> findByMessageIdOrderByTimestampAsc(String messageId);
    
    /**
     * Find latest status history entry for a message.
     */
    MessageStatusHistory findFirstByMessageIdOrderByTimestampDesc(String messageId);
}

