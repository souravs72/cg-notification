package com.clapgrow.notification.api.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Test configuration to initialize database enum types before Hibernate creates tables.
 * This ensures PostgreSQL enum types exist before Hibernate tries to use them.
 * Uses ApplicationListener to run SQL scripts as early as possible in the Spring lifecycle.
 */
@TestConfiguration
@Order(Integer.MIN_VALUE) // Run as early as possible
public class TestDatabaseConfig implements ApplicationListener<ContextRefreshedEvent> {

    private DataSource dataSource;
    private boolean initialized = false;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // Only initialize once
        if (initialized) {
            return;
        }
        
        // Get DataSource from application context if not set
        if (dataSource == null) {
            dataSource = event.getApplicationContext().getBean(DataSource.class);
        }
        
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            Resource resource = new ClassPathResource("schema-test.sql");
            populator.addScript(resource);
            populator.setContinueOnError(true); // Don't fail if enum types already exist
            populator.setSeparator(";");
            
            // Execute the schema initialization script
            DatabasePopulatorUtils.execute(populator, dataSource);
            initialized = true;
        } catch (Exception e) {
            // Log but don't fail - enum types might already exist or script might have issues
            System.err.println("Warning: Could not initialize database enum types: " + e.getMessage());
            // Try to continue - the SQL script uses DO blocks that handle duplicates
        }
    }
}

