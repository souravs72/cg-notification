package com.clapgrow.notification.api.enums;

/**
 * Subscription status for user accounts.
 * Stored as String in database for compatibility, but used as enum in Java code for type safety.
 */
public enum SubscriptionStatus {
    ACTIVE,
    EXPIRED,
    CANCELLED
}

