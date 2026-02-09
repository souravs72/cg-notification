package com.clapgrow.notification.api.service;

import com.clapgrow.notification.api.enums.NotificationChannel;
import io.awspring.cloud.sns.core.SnsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Publishes notification payloads to SNS topics (replaces Kafka producer).
 * Messages are published after DB commit by callers using TransactionSynchronization.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnsNotificationSender {

    private final SnsTemplate snsTemplate;

    @Value("${messaging.sns.topics.email:notifications-email}")
    private String emailTopicName;

    @Value("${messaging.sns.topics.whatsapp:notifications-whatsapp}")
    private String whatsappTopicName;

    /**
     * Publish JSON payload to the channel-specific SNS topic.
     * Destination is resolved by topic name (Spring Cloud AWS resolves to ARN when needed).
     *
     * @param channel   Notification channel (EMAIL or WHATSAPP)
     * @param messageId Message ID (used as SNS subject for tracing)
     * @param jsonPayload JSON body
     */
    public void publish(NotificationChannel channel, String messageId, String jsonPayload) {
        String topicName = getTopicNameForChannel(channel);
        snsTemplate.sendNotification(topicName, jsonPayload, messageId);
        log.debug("Published message {} to SNS topic {}", messageId, topicName);
    }

    public String getTopicNameForChannel(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailTopicName;
            case WHATSAPP -> whatsappTopicName;
            default -> throw new IllegalArgumentException("Unsupported channel: " + channel);
        };
    }
}
