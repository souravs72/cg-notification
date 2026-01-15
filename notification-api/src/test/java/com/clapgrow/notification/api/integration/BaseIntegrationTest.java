package com.clapgrow.notification.api.integration;

import com.clapgrow.notification.api.NotificationApiApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests.
 * Uses environment variables for database and Kafka configuration (provided by CI services).
 * For local development, ensure PostgreSQL and Kafka are running or use Testcontainers.
 */
@SpringBootTest(classes = NotificationApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestDatabaseConfig.class)
public abstract class BaseIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Use environment variables (CI provides these via services)
        String dbUrl = System.getenv("SPRING_DATASOURCE_URL");
        String dbUser = System.getenv("SPRING_DATASOURCE_USERNAME");
        String dbPass = System.getenv("SPRING_DATASOURCE_PASSWORD");
        String kafkaServers = System.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS");
        
        // Fallback to defaults if not set
        registry.add("spring.datasource.url", () -> 
            dbUrl != null ? dbUrl : "jdbc:postgresql://localhost:5432/notification_db");
        registry.add("spring.datasource.username", () -> 
            dbUser != null ? dbUser : "notification_user");
        registry.add("spring.datasource.password", () -> 
            dbPass != null ? dbPass : "notification_pass");
        registry.add("spring.kafka.bootstrap-servers", () -> 
            kafkaServers != null ? kafkaServers : "localhost:9092");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        // Disable Spring Boot's SQL initialization - we use TestDatabaseConfig instead
        // TestDatabaseConfig uses InitializingBean to run SQL scripts before Hibernate
        registry.add("spring.sql.init.mode", () -> "never");
    }
}

