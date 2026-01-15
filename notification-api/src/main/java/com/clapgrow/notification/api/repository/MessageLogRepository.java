package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageLogRepository extends JpaRepository<MessageLog, UUID> {
    Optional<MessageLog> findByMessageId(String messageId);
    
    List<MessageLog> findBySiteIdAndChannel(UUID siteId, NotificationChannel channel);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id = :siteId AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countBySiteIdAndStatus(@Param("siteId") UUID siteId, @Param("status") String status);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id = :siteId AND m.channel = CAST(:channel AS notification_channel)", nativeQuery = true)
    Long countBySiteIdAndChannel(@Param("siteId") UUID siteId, @Param("channel") String channel);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id = :siteId AND m.channel = CAST(:channel AS notification_channel) AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countBySiteIdAndChannelAndStatus(
        @Param("siteId") UUID siteId, 
        @Param("channel") String channel,
        @Param("status") String status
    );
    
    @Query("SELECT m FROM MessageLog m WHERE m.siteId = :siteId AND m.createdAt BETWEEN :startDate AND :endDate")
    List<MessageLog> findBySiteIdAndDateRange(
        @Param("siteId") UUID siteId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    Page<MessageLog> findBySiteId(UUID siteId, Pageable pageable);
    
    Page<MessageLog> findBySiteIdAndStatus(UUID siteId, DeliveryStatus status, Pageable pageable);
    
    Page<MessageLog> findBySiteIdAndChannel(UUID siteId, NotificationChannel channel, Pageable pageable);
    
    Page<MessageLog> findBySiteIdAndStatusAndChannel(
        UUID siteId, 
        DeliveryStatus status, 
        NotificationChannel channel, 
        Pageable pageable
    );
    
    Page<MessageLog> findBySiteIdAndCreatedAtBetween(
        UUID siteId,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );
    
    Page<MessageLog> findBySiteIdAndStatusAndChannelAndCreatedAtBetween(
        UUID siteId,
        DeliveryStatus status,
        NotificationChannel channel,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Pageable pageable
    );
    
    @Query("SELECT COUNT(m) FROM MessageLog m WHERE m.siteId = :siteId")
    Long countBySiteId(@Param("siteId") UUID siteId);
    
    List<MessageLog> findBySiteId(UUID siteId);
    
    @Query(value = "SELECT * FROM message_logs m WHERE m.status = CAST(:status AS delivery_status) AND m.scheduled_at <= :now ORDER BY m.scheduled_at ASC", nativeQuery = true)
    List<MessageLog> findByStatusAndScheduledAtLessThanEqual(
        @Param("status") String status,
        @Param("now") LocalDateTime now
    );
    
    @Query(value = "SELECT * FROM message_logs m WHERE m.status = CAST(:status AS delivery_status) ORDER BY m.scheduled_at ASC", nativeQuery = true)
    Page<MessageLog> findByStatusOrderByScheduledAtAsc(
        @Param("status") String status,
        Pageable pageable
    );
    
    // Methods for messages without sites (siteId is null)
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id IS NULL AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countByNullSiteIdAndStatus(@Param("status") String status);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id IS NULL AND m.channel = CAST(:channel AS notification_channel)", nativeQuery = true)
    Long countByNullSiteIdAndChannel(@Param("channel") String channel);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id IS NULL AND m.channel = CAST(:channel AS notification_channel) AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countByNullSiteIdAndChannelAndStatus(
        @Param("channel") String channel,
        @Param("status") String status
    );
    
    // Methods for all messages (with or without sites)
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countAllByStatus(@Param("status") String status);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.channel = CAST(:channel AS notification_channel)", nativeQuery = true)
    Long countAllByChannel(@Param("channel") String channel);
    
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.channel = CAST(:channel AS notification_channel) AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countAllByChannelAndStatus(
        @Param("channel") String channel,
        @Param("status") String status
    );
}

