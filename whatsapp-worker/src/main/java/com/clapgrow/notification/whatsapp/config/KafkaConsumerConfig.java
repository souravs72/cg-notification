package com.clapgrow.notification.whatsapp.config;

import com.clapgrow.notification.common.kafka.KafkaConsumerConfigHelper;
import org.apache.kafka.common.config.SaslConfigs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final String SECURITY_PROTOCOL = "security.protocol";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:whatsapp-worker-group}")
    private String baseGroupId;

    @Value("${kafka.consumer.environment-prefix:}")
    private String environmentPrefix;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    @Value("${spring.kafka.msk-iam-enabled:false}")
    private boolean mskIamEnabled;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        String groupId = KafkaConsumerConfigHelper.buildGroupId(baseGroupId, environmentPrefix);
        Map<String, Object> configProps = KafkaConsumerConfigHelper.createBaseConsumerProperties(
            bootstrapServers, groupId);
        if (mskIamEnabled) {
            configProps.put(SECURITY_PROTOCOL, "SASL_SSL");
            configProps.put(SaslConfigs.SASL_MECHANISM, "AWS_MSK_IAM");
            configProps.put(SaslConfigs.SASL_JAAS_CONFIG,
                "software.amazon.msk.auth.iam.IAMLoginModule required;");
            configProps.put(SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS,
                "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        }
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(concurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}

