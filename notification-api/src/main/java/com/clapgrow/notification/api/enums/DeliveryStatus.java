package com.clapgrow.notification.api.enums;

public enum DeliveryStatus {
    PENDING,     // Initial message queued for processing
    RETRYING,    // Message being retried after failure (semantically distinct from PENDING)
    SCHEDULED,
    SENT,
    DELIVERED,
    FAILED,
    BOUNCED,
    REJECTED
}

