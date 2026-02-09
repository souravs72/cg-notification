package com.clapgrow.notification.api.enums;

/**
 * Type of failure for a message.
 * Used to distinguish between Kafka publish failures and consumer processing failures.
 * This allows efficient querying without filtering in Java.
 */
public enum FailureType {
    KAFKA,      // Failure during Kafka publish (producer-side)
    CONSUMER    // Failure during consumer processing (consumer-side)
}








