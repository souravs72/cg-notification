package com.clapgrow.notification.api.integration;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Test configuration to initialize database enum types before Hibernate creates tables.
 * This ensures PostgreSQL enum types exist before Hibernate tries to use them.
 * Uses InitializingBean to execute SQL scripts during bean initialization, before Hibernate.
 */
@TestConfiguration
@Order(Integer.MIN_VALUE) // Run as early as possible
@DependsOn("dataSource")
public class TestDatabaseConfig implements InitializingBean, ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private DataSource dataSource;
    
    private boolean enumTypesCreated = false;

    @Override
    public void afterPropertiesSet() {
        // Execute SQL script to create enum types before Hibernate creates tables
        // This runs before Hibernate's EntityManagerFactory is created
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Execute statements individually to ensure they all run
            // Create extensions
            try {
                statement.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            } catch (Exception e) {
                // Extension might already exist, ignore
            }
            
            try {
                statement.execute("CREATE EXTENSION IF NOT EXISTS \"pg_trgm\"");
            } catch (Exception e) {
                // Extension might already exist, ignore
            }
            
            // Create enum types using DO blocks - execute each separately
            String createDeliveryStatusEnum = 
                "DO $$ BEGIN " +
                "CREATE TYPE delivery_status AS ENUM ('PENDING', 'SCHEDULED', 'SENT', 'DELIVERED', 'FAILED', 'BOUNCED', 'REJECTED'); " +
                "EXCEPTION WHEN duplicate_object THEN null; " +
                "END $$;";
            try {
                statement.execute(createDeliveryStatusEnum);
            } catch (Exception e) {
                // Type might already exist, that's okay
                if (!e.getMessage().contains("already exists") && !e.getMessage().contains("duplicate")) {
                    System.err.println("Warning creating delivery_status enum: " + e.getMessage());
                }
            }
            
            String createNotificationChannelEnum = 
                "DO $$ BEGIN " +
                "CREATE TYPE notification_channel AS ENUM ('EMAIL', 'WHATSAPP', 'SMS', 'PUSH'); " +
                "EXCEPTION WHEN duplicate_object THEN null; " +
                "END $$;";
            try {
                statement.execute(createNotificationChannelEnum);
            } catch (Exception e) {
                // Type might already exist, that's okay
                if (!e.getMessage().contains("already exists") && !e.getMessage().contains("duplicate")) {
                    System.err.println("Warning creating notification_channel enum: " + e.getMessage());
                }
            }
            
            // Verify enum types were created (or already exist)
            try (ResultSet rs = statement.executeQuery(
                    "SELECT typname FROM pg_type WHERE typname IN ('delivery_status', 'notification_channel')")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                if (count < 2) {
                    System.err.println("Warning: Expected 2 enum types, found " + count);
                }
            }
            
        } catch (Exception e) {
            // Log but don't fail - enum types might already exist
            System.err.println("Warning: Could not initialize database enum types: " + e.getMessage());
            // Don't print full stack trace for expected errors (duplicate types)
            if (!e.getMessage().contains("already exists") && 
                !e.getMessage().contains("duplicate")) {
                e.printStackTrace();
            }
            // Don't rethrow - enum types might already exist from previous test runs
        }
        enumTypesCreated = true;
    }
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // After context is refreshed, verify enum types exist and log if tables were created
        if (enumTypesCreated) {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                     "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename IN ('message_logs', 'frappe_sites')")) {
                int tableCount = 0;
                while (rs.next()) {
                    tableCount++;
                }
                if (tableCount == 0) {
                    System.err.println("Warning: No tables found after context refresh. Hibernate may not have created tables.");
                }
            } catch (Exception e) {
                // Ignore - this is just for debugging
            }
        }
    }
}
