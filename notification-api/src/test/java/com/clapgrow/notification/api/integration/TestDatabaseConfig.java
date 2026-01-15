package com.clapgrow.notification.api.integration;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Test configuration to initialize database enum types before Hibernate creates tables.
 * This ensures PostgreSQL enum types exist before Hibernate tries to use them.
 * Uses InitializingBean to execute SQL scripts during bean initialization, before Hibernate.
 */
@TestConfiguration
@Order(Integer.MIN_VALUE) // Run as early as possible
@DependsOn("dataSource")
public class TestDatabaseConfig implements InitializingBean {

    @Autowired
    private DataSource dataSource;

    @Override
    public void afterPropertiesSet() {
        // Execute SQL script directly as a single statement to handle multi-line DO blocks
        // This runs before Hibernate's EntityManagerFactory is created
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Read the entire SQL script as a single string
            ClassPathResource resource = new ClassPathResource("schema-test.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // Execute the entire script as one statement
            statement.execute(sql);
        } catch (Exception e) {
            // Log but don't fail - enum types might already exist
            System.err.println("Warning: Could not initialize database enum types: " + e.getMessage());
            // Don't print full stack trace for expected errors (duplicate types)
            if (!e.getMessage().contains("already exists") && !e.getMessage().contains("duplicate")) {
                e.printStackTrace();
            }
            // Don't rethrow - the SQL script uses DO blocks that handle duplicates gracefully
        }
    }
}
