package com.clapgrow.notification.api.integration;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Test configuration to initialize database enum types before Hibernate creates tables.
 * This ensures PostgreSQL enum types exist before Hibernate tries to use them.
 * Uses @PostConstruct to execute SQL scripts immediately after DataSource is available.
 */
@TestConfiguration
@Order(Integer.MIN_VALUE) // Run as early as possible
public class TestDatabaseConfig {

    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void initializeDatabase() {
        // Execute SQL script directly using ScriptUtils to ensure it runs early
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-test.sql"));
        } catch (Exception e) {
            // Log but don't fail - enum types might already exist
            System.err.println("Warning: Could not initialize database enum types: " + e.getMessage());
            // Don't rethrow - the SQL script uses DO blocks that handle duplicates gracefully
        }
    }
}
