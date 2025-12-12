package com.clapgrow.notification.proto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationProtoTest {
    
    @Test
    void testNotificationChannelEnum() {
        assertNotNull(NotificationChannel.EMAIL);
        assertNotNull(NotificationChannel.WHATSAPP);
        assertNotNull(NotificationChannel.SMS);
        assertNotNull(NotificationChannel.PUSH);
    }
    
    @Test
    void testDeliveryStatusEnum() {
        assertNotNull(DeliveryStatus.PENDING);
        assertNotNull(DeliveryStatus.SENT);
        assertNotNull(DeliveryStatus.DELIVERED);
        assertNotNull(DeliveryStatus.FAILED);
        assertNotNull(DeliveryStatus.BOUNCED);
        assertNotNull(DeliveryStatus.REJECTED);
    }
    
    @Test
    void testChannelMetricsBuilder() {
        ChannelMetrics metrics = ChannelMetrics.newBuilder()
            .setChannel(NotificationChannel.EMAIL)
            .setTotalSent(100)
            .setTotalSuccess(95)
            .setTotalFailed(5)
            .build();
        
        assertEquals(NotificationChannel.EMAIL, metrics.getChannel());
        assertEquals(100, metrics.getTotalSent());
        assertEquals(95, metrics.getTotalSuccess());
        assertEquals(5, metrics.getTotalFailed());
    }
}

