package com.clapgrow.notification.common.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared utility for creating production-ready Kafka consumer configurations.
 * 
 * <p>Centralizes common consumer settings to prevent duplication and configuration drift
 * across worker modules. This ensures consistent Kafka consumer behavior across
 * all workers and makes cross-worker tuning easier.
 * 
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * String groupId = KafkaConsumerConfigHelper.buildGroupId("email-worker-group", "prod");
 * Map<String, Object> props = KafkaConsumerConfigHelper.createBaseConsumerProperties(
 *     bootstrapServers, groupId);
 * }</pre>
 */
public final class KafkaConsumerConfigHelper {
    
    private KafkaConsumerConfigHelper() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Creates base consumer configuration properties with production-ready defaults.
     * 
     * <p>This method centralizes all common Kafka consumer settings including:
     * <ul>
     *   <li>Connection and serialization settings</li>
     *   <li>Offset management (manual commit, earliest reset)</li>
     *   <li>Production-ready timeout and heartbeat settings</li>
     *   <li>Fetch optimization parameters</li>
     *   <li>Cooperative rebalancing (prevents "stop-the-world" rebalances in Kubernetes)</li>
     * </ul>
     * 
     * <p><strong>Future enhancement:</strong> Consider accepting a {@code ConsumerTuningProperties}
     * object to expose additional knobs like {@code max-poll-records} and {@code session-timeout-ms}
     * via configuration properties for fine-tuning per environment.
     * 
     * @param bootstrapServers Kafka bootstrap servers (e.g., "localhost:9092")
     * @param groupId Consumer group ID (should include environment prefix for isolation)
     * @return Map of consumer configuration properties ready for DefaultKafkaConsumerFactory
     */
    public static Map<String, Object> createBaseConsumerProperties(String bootstrapServers, String groupId) {
        Map<String, Object> configProps = new HashMap<>();
        
        // Connection settings
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Serialization
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Offset management
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Polling configuration
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        
        // Production-ready timeout and heartbeat settings
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        
        // Fetch optimization
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
        
        // Cooperative rebalancing (prevents "stop-the-world" rebalances)
        // Especially valuable in Kubernetes when pods scale up/down
        // Allows consumers to continue processing during rebalancing
        configProps.put(
            ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
            "org.apache.kafka.clients.consumer.CooperativeStickyAssignor"
        );
        
        return configProps;
    }
    
    /**
     * Builds a consumer group ID with optional environment prefix.
     * 
     * <p>Supports environment isolation (prod, staging, dev) and deployment strategies
     * like blue/green or canary deployments.
     * 
     * <p><strong>Examples:</strong>
     * <ul>
     *   <li>{@code buildGroupId("email-worker-group", "prod")} → "prod-email-worker-group"</li>
     *   <li>{@code buildGroupId("email-worker-group", "staging")} → "staging-email-worker-group"</li>
     *   <li>{@code buildGroupId("email-worker-group", "dev")} → "dev-email-worker-group"</li>
     *   <li>{@code buildGroupId("email-worker-group", null)} → "email-worker-group"</li>
     *   <li>{@code buildGroupId("email-worker-group", "")} → "email-worker-group"</li>
     * </ul>
     * 
     * @param baseGroupId Base group ID (e.g., "email-worker-group", "whatsapp-worker-group")
     * @param environmentPrefix Optional environment prefix (e.g., "prod", "staging", "dev", "blue", "green")
     *                          Can be null or empty string for no prefix
     * @return Full group ID with prefix if provided, otherwise returns baseGroupId
     */
    public static String buildGroupId(String baseGroupId, String environmentPrefix) {
        if (environmentPrefix != null && !environmentPrefix.trim().isEmpty()) {
            return environmentPrefix.trim() + "-" + baseGroupId;
        }
        return baseGroupId;
    }
}

