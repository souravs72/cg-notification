package com.clapgrow.notification.api.integration;

import com.clapgrow.notification.api.NotificationApiApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using Testcontainers.
 * Provides PostgreSQL for isolated testing. SNS/SQS are not started; use mocks or LocalStack if needed.
 *
 * Note: These tests require Docker to be running.
 * To skip integration tests when Docker is unavailable: mvn verify -DskipITs=true
 */
@SpringBootTest(
    classes = NotificationApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    @SuppressWarnings("resource") // Testcontainers manages lifecycle automatically
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:15-alpine")
    )
        .withDatabaseName("notification_db")
        .withUsername("notification_user")
        .withPassword("notification_pass")
        .withInitScript("schema-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
        registry.add("spring.cloud.aws.credentials.access-key", () -> "test");
        registry.add("spring.cloud.aws.credentials.secret-key", () -> "test");
        registry.add("messaging.sns.topics.email", () -> "notifications-email");
        registry.add("messaging.sns.topics.whatsapp", () -> "notifications-whatsapp");
        registry.add("messaging.sqs.queues.email-dlq", () -> "notifications-email-dlq");
        registry.add("messaging.sqs.queues.whatsapp-dlq", () -> "notifications-whatsapp-dlq");

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "false");

        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "600000");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "1800000");

        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:schema-test.sql");
        registry.add("spring.sql.init.continue-on-error", () -> "true");
    }
}
