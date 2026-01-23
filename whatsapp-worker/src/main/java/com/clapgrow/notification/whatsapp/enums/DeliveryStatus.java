package com.clapgrow.notification.whatsapp.enums;

/**
 * Delivery status for messages.
 * 
 * ⚠️ CRITICAL: This enum is duplicated in whatsapp-worker and email-worker modules
 * because workers cannot depend on notification-api module (circular dependency risk).
 * 
 * IMPORTANT: Values MUST match exactly:
 * 1. The DeliveryStatus enum in notification-api module
 * 2. The PostgreSQL delivery_status enum type in the database
 * 
 * If you add/modify values here, you MUST:
 * 1. Update notification-api/src/main/java/com/clapgrow/notification/api/enums/DeliveryStatus.java
 * 2. Update email-worker's DeliveryStatus enum (if it exists)
 * 3. Update the PostgreSQL enum type via migration script
 * 4. Update all SQL queries that reference delivery_status
 * 
 * This duplication is intentional to maintain module independence,
 * but requires careful synchronization when making changes.
 * 
 * Last synced: 2026-01-22
 */
public enum DeliveryStatus {
    PENDING,
    RETRYING,
    SCHEDULED,
    SENT,
    DELIVERED,
    FAILED,
    BOUNCED,
    REJECTED;
    
    /**
     * Get the string representation for database storage.
     * @return Status name (matches PostgreSQL enum)
     */
    public String getValue() {
        return this.name();
    }
}

