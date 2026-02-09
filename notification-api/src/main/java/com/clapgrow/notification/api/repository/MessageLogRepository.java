package com.clapgrow.notification.api.repository;

import com.clapgrow.notification.api.entity.MessageLog;
import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    
    /**
     * Count messages by site ID and status.
     * 
     * @param siteId Site ID
     * @param status Delivery status (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countBySiteIdAndStatus(UUID siteId, DeliveryStatus status) {
        return countBySiteIdAndStatusInternal(siteId, status.name());
    }
    
    /**
     * Internal helper method for native query execution.
     * Use countBySiteIdAndStatus(UUID, DeliveryStatus) instead.
     * 
     * @param siteId Site ID
     * @param status Status as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id = :siteId AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countBySiteIdAndStatusInternal(@Param("siteId") UUID siteId, @Param("status") String status);
    
    /**
     * Count messages by site ID and channel.
     * 
     * @param siteId Site ID
     * @param channel Notification channel (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countBySiteIdAndChannel(UUID siteId, NotificationChannel channel) {
        return countBySiteIdAndChannelInternal(siteId, channel.name());
    }
    
    /**
     * Count messages by site ID, channel, and status.
     * 
     * @param siteId Site ID
     * @param channel Notification channel (enum)
     * @param status Delivery status (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countBySiteIdAndChannelAndStatus(UUID siteId, NotificationChannel channel, DeliveryStatus status) {
        return countBySiteIdAndChannelAndStatusInternal(siteId, channel.name(), status.name());
    }
    
    /**
     * Internal helper method for native query execution.
     * Use countBySiteIdAndChannel(UUID, NotificationChannel) instead.
     * 
     * @param siteId Site ID
     * @param channel Channel as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id = :siteId AND m.channel = CAST(:channel AS notification_channel)", nativeQuery = true)
    Long countBySiteIdAndChannelInternal(@Param("siteId") UUID siteId, @Param("channel") String channel);
    
    /**
     * Internal helper method for native query execution.
     * Use countBySiteIdAndChannelAndStatus(UUID, NotificationChannel, DeliveryStatus) instead.
     * 
     * @param siteId Site ID
     * @param channel Channel as string (for native query compatibility)
     * @param status Status as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id = :siteId AND m.channel = CAST(:channel AS notification_channel) AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countBySiteIdAndChannelAndStatusInternal(
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
    
    /**
     * Find messages by status list with pagination.
     * Filters at the database level for better performance.
     * 
     * @param statuses List of delivery statuses to filter by
     * @param pageable Pagination parameters
     * @return Page of messages matching the status criteria
     */
    Page<MessageLog> findByStatusIn(List<DeliveryStatus> statuses, Pageable pageable);
    
    /**
     * Find messages by status, failure type, retry count, and creation time.
     * Used for retry jobs to find messages eligible for retry.
     * 
     * ⚠️ PERFORMANCE: Uses failure_type column for efficient querying (no Java-side filtering).
     * Uses Pageable for backpressure to prevent processing too many messages at once.
     * 
     * @param status Delivery status to filter by
     * @param failureType Failure type (KAFKA or CONSUMER)
     * @param maxRetryCount Maximum retry count (exclusive)
     * @param createdBefore Messages created before this timestamp
     * @param pageable Pagination parameters (use PageRequest.of(0, batchSize) for batch limit)
     * @return List of messages matching the criteria (limited to batch size)
     */
    @Query("SELECT m FROM MessageLog m WHERE m.status = :status " +
           "AND m.failureType = :failureType " +
           "AND (m.retryCount IS NULL OR m.retryCount < :maxRetryCount) " +
           "AND m.createdAt < :createdBefore " +
           "ORDER BY m.createdAt ASC")
    List<MessageLog> findFailedMessagesForRetry(
        @Param("status") DeliveryStatus status,
        @Param("failureType") com.clapgrow.notification.api.enums.FailureType failureType,
        @Param("maxRetryCount") Integer maxRetryCount,
        @Param("createdBefore") LocalDateTime createdBefore,
        org.springframework.data.domain.Pageable pageable
    );
    
    @Query("SELECT COUNT(m) FROM MessageLog m WHERE m.siteId = :siteId")
    Long countBySiteId(@Param("siteId") UUID siteId);
    
    /**
     * Get the creation date of the oldest message for a site.
     * Used for calculating average messages per day without loading all messages.
     * Returns null if no messages exist for the site.
     */
    @Query("SELECT MIN(m.createdAt) FROM MessageLog m WHERE m.siteId = :siteId")
    Optional<LocalDateTime> findOldestMessageDateBySiteId(@Param("siteId") UUID siteId);
    
    /**
     * Find all messages for a site (unbounded result set).
     * 
     * ⚠️ WARNING: This method loads ALL messages for a site into memory.
     * 
     * Only use this method when:
     * - You know the site has a small, bounded number of messages
     * - You need all messages for a specific operation (e.g., export)
     * - You are NOT using it for statistics or aggregations
     * 
     * For statistics, use countBySiteId() or paginated queries instead.
     * For large datasets, use findBySiteId(UUID, Pageable) with pagination.
     * 
     * @param siteId Site ID
     * @return List of all messages for the site (may be large!)
     */
    List<MessageLog> findBySiteId(UUID siteId);
    
    /**
     * Find scheduled messages ready for processing.
     * 
     * ⚠️ PERFORMANCE NOTE: Uses SELECT * which fetches all columns.
     * This is acceptable for now, but consider optimizing to SELECT specific columns
     * if message_logs table grows very wide (many columns) in the future.
     * 
     * @param status Status filter (typically SCHEDULED)
     * @param now Current timestamp for comparison
     * @return List of messages ready for processing
     */
    @Query(value = "SELECT * FROM message_logs m WHERE m.status = CAST(:status AS delivery_status) AND m.scheduled_at <= :now ORDER BY m.scheduled_at ASC", nativeQuery = true)
    List<MessageLog> findByStatusAndScheduledAtLessThanEqual(
        @Param("status") String status,
        @Param("now") LocalDateTime now
    );
    
    /**
     * Find scheduled message IDs ready for processing with pagination.
     * 
     * ⚠️ BACKPRESSURE: Uses pagination to prevent hot-loop when backlog grows.
     * Process in batches to avoid one scheduler run taking longer than the interval.
     * 
     * @param status Status filter (typically SCHEDULED)
     * @param now Current timestamp for comparison
     * @param pageable Pagination parameters (use PageRequest.of(0, batchSize) for batch limit)
     * @return List of message IDs ready for processing (limited to batch size)
     */
    @Query(value = "SELECT m.message_id FROM message_logs m WHERE m.status = CAST(:status AS delivery_status) AND m.scheduled_at <= :now ORDER BY m.scheduled_at ASC", nativeQuery = true)
    List<String> findMessageIdsByStatusAndScheduledAtLessThanEqual(
        @Param("status") String status,
        @Param("now") LocalDateTime now,
        org.springframework.data.domain.Pageable pageable
    );
    
    /**
     * Find scheduled messages with pagination.
     * 
     * ⚠️ PERFORMANCE NOTE: Uses SELECT * which fetches all columns.
     * This is acceptable for now, but consider optimizing to SELECT specific columns
     * if message_logs table grows very wide (many columns) in the future.
     * 
     * @param status Status filter (typically SCHEDULED)
     * @param pageable Pagination parameters
     * @return Page of scheduled messages
     */
    @Query(value = "SELECT * FROM message_logs m WHERE m.status = CAST(:status AS delivery_status) AND m.scheduled_at IS NOT NULL ORDER BY m.scheduled_at ASC",
           countQuery = "SELECT count(*) FROM message_logs m WHERE m.status = CAST(:status AS delivery_status) AND m.scheduled_at IS NOT NULL",
           nativeQuery = true)
    Page<MessageLog> findByStatusOrderByScheduledAtAsc(
        @Param("status") String status,
        Pageable pageable
    );
    
    // Methods for messages without sites (siteId is null)
    /**
     * Count messages without a site ID by status.
     * 
     * @param status Delivery status (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countByNullSiteIdAndStatus(DeliveryStatus status) {
        return countByNullSiteIdAndStatusInternal(status.name());
    }
    
    /**
     * Count messages without a site ID by channel.
     * 
     * @param channel Notification channel (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countByNullSiteIdAndChannel(NotificationChannel channel) {
        return countByNullSiteIdAndChannelInternal(channel.name());
    }
    
    /**
     * Count messages without a site ID by channel and status.
     * 
     * @param channel Notification channel (enum)
     * @param status Delivery status (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countByNullSiteIdAndChannelAndStatus(NotificationChannel channel, DeliveryStatus status) {
        return countByNullSiteIdAndChannelAndStatusInternal(channel.name(), status.name());
    }
    
    /**
     * Internal helper method for native query execution.
     * Use countByNullSiteIdAndStatus(DeliveryStatus) instead.
     * 
     * @param status Status as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id IS NULL AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countByNullSiteIdAndStatusInternal(@Param("status") String status);
    
    /**
     * Internal helper method for native query execution.
     * Use countByNullSiteIdAndChannel(NotificationChannel) instead.
     * 
     * @param channel Channel as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id IS NULL AND m.channel = CAST(:channel AS notification_channel)", nativeQuery = true)
    Long countByNullSiteIdAndChannelInternal(@Param("channel") String channel);
    
    /**
     * Internal helper method for native query execution.
     * Use countByNullSiteIdAndChannelAndStatus(NotificationChannel, DeliveryStatus) instead.
     * 
     * @param channel Channel as string (for native query compatibility)
     * @param status Status as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.site_id IS NULL AND m.channel = CAST(:channel AS notification_channel) AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countByNullSiteIdAndChannelAndStatusInternal(
        @Param("channel") String channel,
        @Param("status") String status
    );
    
    // Methods for all messages (with or without sites)
    /**
     * Count all messages by status (regardless of site).
     * 
     * @param status Delivery status (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countAllByStatus(DeliveryStatus status) {
        return countAllByStatusInternal(status.name());
    }
    
    /**
     * Count all messages by channel (regardless of site).
     * 
     * @param channel Notification channel (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countAllByChannel(NotificationChannel channel) {
        return countAllByChannelInternal(channel.name());
    }
    
    /**
     * Count all messages by channel and status (regardless of site).
     * 
     * @param channel Notification channel (enum)
     * @param status Delivery status (enum)
     * @return Count of messages matching the criteria
     * 
     * @apiNote This is the preferred public API. Use this method in services and controllers.
     */
    default Long countAllByChannelAndStatus(NotificationChannel channel, DeliveryStatus status) {
        return countAllByChannelAndStatusInternal(channel.name(), status.name());
    }
    
    /**
     * Internal helper method for native query execution.
     * Use countAllByStatus(DeliveryStatus) instead.
     * 
     * @param status Status as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countAllByStatusInternal(@Param("status") String status);
    
    /**
     * Internal helper method for native query execution.
     * Use countAllByChannel(NotificationChannel) instead.
     * 
     * @param channel Channel as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.channel = CAST(:channel AS notification_channel)", nativeQuery = true)
    Long countAllByChannelInternal(@Param("channel") String channel);
    
    /**
     * Internal helper method for native query execution.
     * Use countAllByChannelAndStatus(NotificationChannel, DeliveryStatus) instead.
     * 
     * @param channel Channel as string (for native query compatibility)
     * @param status Status as string (for native query compatibility)
     * @return Count of messages matching the criteria
     * @internal This method is internal - use the enum-based overload instead
     */
    @Query(value = "SELECT COUNT(m) FROM message_logs m WHERE m.channel = CAST(:channel AS notification_channel) AND m.status = CAST(:status AS delivery_status)", nativeQuery = true)
    Long countAllByChannelAndStatusInternal(
        @Param("channel") String channel,
        @Param("status") String status
    );
    
    /**
     * Atomically update message status from SCHEDULED to PENDING.
     * This prevents duplicate processing when multiple scheduler instances run concurrently.
     * Returns the number of rows affected (should be 1 if successful, 0 if already processed).
     * 
     * ⚠️ EXPLICIT INVARIANT: Clears failure_type when status changes to PENDING
     * This makes intent explicit and keeps Java model aligned with DB constraint.
     * 
     * @param messageId Message ID to update
     * @return Number of rows updated (1 if successful, 0 if already processed or not found)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE message_logs SET status = CAST('PENDING' AS delivery_status), failure_type = NULL, scheduled_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE message_id = :messageId AND status = CAST('SCHEDULED' AS delivery_status)", nativeQuery = true)
    int atomicallyUpdateScheduledToPending(@Param("messageId") String messageId);
    
    /**
     * Atomically claim a FAILED message for retry by updating status to RETRYING.
     * This prevents multiple scheduler instances from retrying the same message concurrently.
     * 
     * Uses RETRYING (not PENDING) to provide semantic clarity:
     * - PENDING: Initial message queued for processing
     * - RETRYING: Message being retried after failure
     * 
     * ⚠️ EXPLICIT INVARIANT: Clears failure_type when status changes from FAILED to RETRYING
     * This makes intent explicit and keeps Java model aligned with DB constraint.
     * 
     * @param messageId Message ID to claim
     * @return Number of rows affected (1 if successfully claimed, 0 if already claimed or not found)
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE message_logs SET status = CAST('RETRYING' AS delivery_status), failure_type = NULL, updated_at = CURRENT_TIMESTAMP WHERE message_id = :messageId AND status = CAST('FAILED' AS delivery_status)", nativeQuery = true)
    int atomicallyClaimFailedMessageForRetry(@Param("messageId") String messageId);
}

