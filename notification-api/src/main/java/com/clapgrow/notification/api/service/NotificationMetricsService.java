package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.enums.DeliveryStatus;
import com.clapgrow.notification.api.enums.NotificationChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Prometheus metrics service for notification system.
 * 
 * Tracks:
 * - Messages sent / failed / retried
 * - Retry count histogram
 * - DLQ count per channel
 * - Messaging (SNS) publish latency
 * 
 * Metrics are exposed at /actuator/prometheus
 * 
 * ⚠️ PERFORMANCE: Counters, timers, and histograms are created once in @PostConstruct
 * and stored in maps keyed by channel. This avoids creating metric objects on every call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationMetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Pre-created counters, timers, and histograms per channel (created once in @PostConstruct)
    private final Map<NotificationChannel, Counter> messageSentCounters = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, Counter> messageFailedCounters = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, Counter> messageRetriedCounters = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, Counter> messageDeliveredCounters = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, Counter> dlqCounters = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, DistributionSummary> retryCountHistograms = new EnumMap<>(NotificationChannel.class);
    private final Map<NotificationChannel, Timer> messagingPublishTimers = new EnumMap<>(NotificationChannel.class);
    
    /**
     * Initialize all metric objects once at startup.
     * This avoids creating metric objects on every call, improving performance.
     */
    @PostConstruct
    void init() {
        for (NotificationChannel channel : NotificationChannel.values()) {
            // ⚠️ METRIC SEMANTICS: "sent" actually means "accepted by API"
            // Message is accepted and persisted; SNS publish happens asynchronously after commit
            messageSentCounters.put(channel, Counter.builder("notification.messages.sent")
                .description("Total number of messages accepted by API (persisted to DB, SNS publish is async)")
                .tag("channel", channel.name())
                .register(meterRegistry));
            
            messageFailedCounters.put(channel, Counter.builder("notification.messages.failed")
                .description("Total number of messages that failed")
                .tag("channel", channel.name())
                .register(meterRegistry));
            
            messageRetriedCounters.put(channel, Counter.builder("notification.messages.retried")
                .description("Total number of messages retried")
                .tag("channel", channel.name())
                .register(meterRegistry));
            
            messageDeliveredCounters.put(channel, Counter.builder("notification.messages.delivered")
                .description("Total number of messages successfully delivered")
                .tag("channel", channel.name())
                .register(meterRegistry));
            
            dlqCounters.put(channel, Counter.builder("notification.messages.dlq")
                .description("Total number of messages sent to dead-letter queue")
                .tag("channel", channel.name())
                .register(meterRegistry));
            
            retryCountHistograms.put(channel, DistributionSummary.builder("notification.retry.count")
                .description("Distribution of retry counts per message")
                .tag("channel", channel.name())
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry));
            
            messagingPublishTimers.put(channel, Timer.builder("notification.messaging.publish.latency")
                .description("Time taken to publish message to SNS")
                .tag("channel", channel.name())
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry));
        }
        log.info("Initialized metrics for {} channels", NotificationChannel.values().length);
    }
    
    // Getters for pre-created metrics
    private Counter getMessageSentCounter(NotificationChannel channel) {
        return messageSentCounters.get(channel);
    }
    
    private Counter getMessageFailedCounter(NotificationChannel channel) {
        return messageFailedCounters.get(channel);
    }
    
    private Counter getMessageRetriedCounter(NotificationChannel channel) {
        return messageRetriedCounters.get(channel);
    }
    
    private Counter getMessageDeliveredCounter(NotificationChannel channel) {
        return messageDeliveredCounters.get(channel);
    }
    
    private Counter getDlqCounter(NotificationChannel channel) {
        return dlqCounters.get(channel);
    }
    
    private DistributionSummary getRetryCountHistogram(NotificationChannel channel) {
        return retryCountHistograms.get(channel);
    }
    
    private Timer getMessagingPublishTimer(NotificationChannel channel) {
        return messagingPublishTimers.get(channel);
    }
    
    /**
     * Record a message sent event.
     */
    public void recordMessageSent(NotificationChannel channel) {
        getMessageSentCounter(channel).increment();
    }
    
    /**
     * Record a message failed event.
     */
    public void recordMessageFailed(NotificationChannel channel) {
        getMessageFailedCounter(channel).increment();
    }
    
    /**
     * Record a message retried event.
     */
    public void recordMessageRetried(NotificationChannel channel, int retryCount) {
        getMessageRetriedCounter(channel).increment();
        getRetryCountHistogram(channel).record(retryCount);
    }
    
    /**
     * Record a message delivered event.
     */
    public void recordMessageDelivered(NotificationChannel channel) {
        getMessageDeliveredCounter(channel).increment();
    }
    
    /**
     * Record a message sent to DLQ.
     */
    public void recordDlq(NotificationChannel channel) {
        getDlqCounter(channel).increment();
    }
    
    /**
     * Record messaging (SNS) publish latency.
     */
    public void recordMessagingPublishLatency(NotificationChannel channel, long durationMillis) {
        getMessagingPublishTimer(channel).record(durationMillis, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record messaging (SNS) publish latency using a Timer.Sample.
     */
    public void recordMessagingPublishLatency(NotificationChannel channel, Timer.Sample sample) {
        sample.stop(getMessagingPublishTimer(channel));
    }
    
    /**
     * Record message status change (convenience method).
     */
    public void recordStatusChange(NotificationChannel channel, DeliveryStatus status, Integer retryCount) {
        switch (status) {
            case PENDING, SCHEDULED, RETRYING, SENT -> {
                // These are intermediate states, don't record as sent/failed
            }
            case DELIVERED -> recordMessageDelivered(channel);
            case FAILED, BOUNCED, REJECTED -> {
                recordMessageFailed(channel);
                if (retryCount != null && retryCount > 0) {
                    recordMessageRetried(channel, retryCount);
                }
            }
        }
    }
}

