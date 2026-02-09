package com.clapgrow.notification.api.enums;

/**
 * Subscription type for user accounts.
 * Stored as String in database for compatibility, but used as enum in Java code for type safety.
 */
public enum SubscriptionType {
    FREE_TRIAL,
    PAID
}

