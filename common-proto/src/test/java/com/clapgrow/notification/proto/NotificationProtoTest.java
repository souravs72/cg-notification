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
        assertNotNull(NotificationChannel.EMAIL);
        assertNotNull(NotificationChannel.WHATSAPP);
        assertNotNull(NotificationChannel.SMS);
        assertNotNull(NotificationChannel.PUSH);
        
        // Verify enum values match proto definition
        assertEquals(0, NotificationChannel.EMAIL.getNumber());
        assertEquals(1, NotificationChannel.WHATSAPP.getNumber());
        assertEquals(2, NotificationChannel.SMS.getNumber());
        assertEquals(3, NotificationChannel.PUSH.getNumber());
        
        // Test forNumber method
        assertEquals(NotificationChannel.EMAIL, NotificationChannel.forNumber(0));
        assertEquals(NotificationChannel.WHATSAPP, NotificationChannel.forNumber(1));
        assertEquals(NotificationChannel.SMS, NotificationChannel.forNumber(2));
        assertEquals(NotificationChannel.PUSH, NotificationChannel.forNumber(3));
        assertNull(NotificationChannel.forNumber(999)); // Invalid number
        
        // Test valueOf method
        assertEquals(NotificationChannel.EMAIL, NotificationChannel.valueOf("EMAIL"));
        assertEquals(NotificationChannel.WHATSAPP, NotificationChannel.valueOf("WHATSAPP"));
        assertEquals(NotificationChannel.SMS, NotificationChannel.valueOf("SMS"));
        assertEquals(NotificationChannel.PUSH, NotificationChannel.valueOf("PUSH"));
    }
    
    @Test
    void testDeliveryStatusEnum() {
        // Test that all enum values exist (generated from notification.proto)
        assertNotNull(DeliveryStatus.PENDING);
        assertNotNull(DeliveryStatus.SENT);
        assertNotNull(DeliveryStatus.DELIVERED);
        assertNotNull(DeliveryStatus.FAILED);
        assertNotNull(DeliveryStatus.BOUNCED);
        assertNotNull(DeliveryStatus.REJECTED);
        
        // Verify enum values match proto definition
        assertEquals(0, DeliveryStatus.PENDING.getNumber());
        assertEquals(1, DeliveryStatus.SENT.getNumber());
        assertEquals(2, DeliveryStatus.DELIVERED.getNumber());
        assertEquals(3, DeliveryStatus.FAILED.getNumber());
        assertEquals(4, DeliveryStatus.BOUNCED.getNumber());
        assertEquals(5, DeliveryStatus.REJECTED.getNumber());
        
        // Test forNumber method
        assertEquals(DeliveryStatus.PENDING, DeliveryStatus.forNumber(0));
        assertEquals(DeliveryStatus.SENT, DeliveryStatus.forNumber(1));
        assertEquals(DeliveryStatus.DELIVERED, DeliveryStatus.forNumber(2));
        assertEquals(DeliveryStatus.FAILED, DeliveryStatus.forNumber(3));
        assertEquals(DeliveryStatus.BOUNCED, DeliveryStatus.forNumber(4));
        assertEquals(DeliveryStatus.REJECTED, DeliveryStatus.forNumber(5));
        assertNull(DeliveryStatus.forNumber(999)); // Invalid number
        
        // Test valueOf method
        assertEquals(DeliveryStatus.PENDING, DeliveryStatus.valueOf("PENDING"));
        assertEquals(DeliveryStatus.SENT, DeliveryStatus.valueOf("SENT"));
        assertEquals(DeliveryStatus.DELIVERED, DeliveryStatus.valueOf("DELIVERED"));
        assertEquals(DeliveryStatus.FAILED, DeliveryStatus.valueOf("FAILED"));
        assertEquals(DeliveryStatus.BOUNCED, DeliveryStatus.valueOf("BOUNCED"));
        assertEquals(DeliveryStatus.REJECTED, DeliveryStatus.valueOf("REJECTED"));
    }
    
    @Test
    void testChannelMetricsBuilder() {
        // Test ChannelMetrics message builder (generated from notification.proto)
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
        
        // Test default values
        ChannelMetrics defaultMetrics = ChannelMetrics.newBuilder().build();
        assertEquals(0, defaultMetrics.getTotalSent());
        assertEquals(0, defaultMetrics.getTotalSuccess());
        assertEquals(0, defaultMetrics.getTotalFailed());
    }
    
    @Test
    void testDeliveryStatusRequest() {
        DeliveryStatusRequest request = DeliveryStatusRequest.newBuilder()
            .setMessageId("MSG-1234567890ABCDEF123456")
            .setSiteId("550e8400-e29b-41d4-a716-446655440000")
            .setChannel(NotificationChannel.WHATSAPP)
            .setStatus(DeliveryStatus.DELIVERED)
            .setErrorMessage("")
            .setTimestamp(System.currentTimeMillis())
            .putMetadata("key1", "value1")
            .putMetadata("key2", "value2")
            .build();
        
        assertEquals("MSG-1234567890ABCDEF123456", request.getMessageId());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", request.getSiteId());
        assertEquals(NotificationChannel.WHATSAPP, request.getChannel());
        assertEquals(DeliveryStatus.DELIVERED, request.getStatus());
        assertEquals(2, request.getMetadataCount());
        assertEquals("value1", request.getMetadataMap().get("key1"));
        assertEquals("value2", request.getMetadataMap().get("key2"));
    }
    
    @Test
    void testDeliveryStatusResponse() {
        DeliveryStatusResponse successResponse = DeliveryStatusResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Status updated successfully")
            .build();
        
        assertTrue(successResponse.getSuccess());
        assertEquals("Status updated successfully", successResponse.getMessage());
        
        DeliveryStatusResponse failureResponse = DeliveryStatusResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Failed to update status")
            .build();
        
        assertFalse(failureResponse.getSuccess());
        assertEquals("Failed to update status", failureResponse.getMessage());
    }
    
    @Test
    void testMetricsRequest() {
        long startTime = System.currentTimeMillis() - 86400000; // 24 hours ago
        long endTime = System.currentTimeMillis();
        
        MetricsRequest request = MetricsRequest.newBuilder()
            .setSiteId("550e8400-e29b-41d4-a716-446655440000")
            .setStartTimestamp(startTime)
            .setEndTimestamp(endTime)
            .setChannel(NotificationChannel.EMAIL)
            .build();
        
        assertEquals("550e8400-e29b-41d4-a716-446655440000", request.getSiteId());
        assertEquals(startTime, request.getStartTimestamp());
        assertEquals(endTime, request.getEndTimestamp());
        assertEquals(NotificationChannel.EMAIL, request.getChannel());
    }
    
    @Test
    void testMetricsResponse() {
        MetricsResponse response = MetricsResponse.newBuilder()
            .setSiteId("550e8400-e29b-41d4-a716-446655440000")
            .setTotalSent(1000)
            .setTotalSuccess(950)
            .setTotalFailed(50)
            .putChannelMetrics("EMAIL", ChannelMetrics.newBuilder()
                .setChannel(NotificationChannel.EMAIL)
                .setTotalSent(600)
                .setTotalSuccess(570)
                .setTotalFailed(30)
                .build())
            .putChannelMetrics("WHATSAPP", ChannelMetrics.newBuilder()
                .setChannel(NotificationChannel.WHATSAPP)
                .setTotalSent(400)
                .setTotalSuccess(380)
                .setTotalFailed(20)
                .build())
            .addDailyMetrics(DailyMetrics.newBuilder()
                .setDate("2026-01-24")
                .setTotalSent(100)
                .setTotalSuccess(95)
                .setTotalFailed(5)
                .build())
            .build();
        
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.getSiteId());
        assertEquals(1000, response.getTotalSent());
        assertEquals(950, response.getTotalSuccess());
        assertEquals(50, response.getTotalFailed());
        assertEquals(2, response.getChannelMetricsCount());
        assertEquals(1, response.getDailyMetricsCount());
        
        ChannelMetrics emailMetrics = response.getChannelMetricsMap().get("EMAIL");
        assertNotNull(emailMetrics);
        assertEquals(NotificationChannel.EMAIL, emailMetrics.getChannel());
        assertEquals(600, emailMetrics.getTotalSent());
    }
    
    @Test
    void testDailyMetrics() {
        DailyMetrics dailyMetrics = DailyMetrics.newBuilder()
            .setDate("2026-01-24")
            .setTotalSent(200)
            .setTotalSuccess(190)
            .setTotalFailed(10)
            .putChannelMetrics("EMAIL", ChannelMetrics.newBuilder()
                .setChannel(NotificationChannel.EMAIL)
                .setTotalSent(120)
                .setTotalSuccess(114)
                .setTotalFailed(6)
                .build())
            .build();
        
        assertEquals("2026-01-24", dailyMetrics.getDate());
        assertEquals(200, dailyMetrics.getTotalSent());
        assertEquals(190, dailyMetrics.getTotalSuccess());
        assertEquals(10, dailyMetrics.getTotalFailed());
        assertEquals(1, dailyMetrics.getChannelMetricsCount());
    }
}

