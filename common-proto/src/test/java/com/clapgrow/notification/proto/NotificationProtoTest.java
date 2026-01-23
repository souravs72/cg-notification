package com.clapgrow.notification.proto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for protobuf-generated classes.
 * 
 * Note: These tests require the protobuf classes to be generated first.
 * Run `mvn generate-sources` or `mvn compile` to generate the classes from notification.proto.
 */
class NotificationProtoTest {
    
    @Test
    void testNotificationChannelEnum() {
        // Test that all enum values exist (generated from notification.proto)
        assertNotNull(com.clapgrow.notification.proto.NotificationChannel.EMAIL);
        assertNotNull(com.clapgrow.notification.proto.NotificationChannel.WHATSAPP);
        assertNotNull(com.clapgrow.notification.proto.NotificationChannel.SMS);
        assertNotNull(com.clapgrow.notification.proto.NotificationChannel.PUSH);
        
        // Verify enum values match proto definition
        assertEquals(0, com.clapgrow.notification.proto.NotificationChannel.EMAIL.getNumber());
        assertEquals(1, com.clapgrow.notification.proto.NotificationChannel.WHATSAPP.getNumber());
        assertEquals(2, com.clapgrow.notification.proto.NotificationChannel.SMS.getNumber());
        assertEquals(3, com.clapgrow.notification.proto.NotificationChannel.PUSH.getNumber());
    }
    
    @Test
    void testDeliveryStatusEnum() {
        // Test that all enum values exist (generated from notification.proto)
        assertNotNull(com.clapgrow.notification.proto.DeliveryStatus.PENDING);
        assertNotNull(com.clapgrow.notification.proto.DeliveryStatus.SENT);
        assertNotNull(com.clapgrow.notification.proto.DeliveryStatus.DELIVERED);
        assertNotNull(com.clapgrow.notification.proto.DeliveryStatus.FAILED);
        assertNotNull(com.clapgrow.notification.proto.DeliveryStatus.BOUNCED);
        assertNotNull(com.clapgrow.notification.proto.DeliveryStatus.REJECTED);
        
        // Verify enum values match proto definition
        assertEquals(0, com.clapgrow.notification.proto.DeliveryStatus.PENDING.getNumber());
        assertEquals(1, com.clapgrow.notification.proto.DeliveryStatus.SENT.getNumber());
        assertEquals(2, com.clapgrow.notification.proto.DeliveryStatus.DELIVERED.getNumber());
        assertEquals(3, com.clapgrow.notification.proto.DeliveryStatus.FAILED.getNumber());
        assertEquals(4, com.clapgrow.notification.proto.DeliveryStatus.BOUNCED.getNumber());
        assertEquals(5, com.clapgrow.notification.proto.DeliveryStatus.REJECTED.getNumber());
    }
    
    @Test
    void testChannelMetricsBuilder() {
        // Test ChannelMetrics message builder (generated from notification.proto)
        com.clapgrow.notification.proto.ChannelMetrics metrics = 
            com.clapgrow.notification.proto.ChannelMetrics.newBuilder()
            .setChannel(com.clapgrow.notification.proto.NotificationChannel.EMAIL)
            .setTotalSent(100)
            .setTotalSuccess(95)
            .setTotalFailed(5)
            .build();
        
        assertEquals(com.clapgrow.notification.proto.NotificationChannel.EMAIL, metrics.getChannel());
        assertEquals(100, metrics.getTotalSent());
        assertEquals(95, metrics.getTotalSuccess());
        assertEquals(5, metrics.getTotalFailed());
    }
}

