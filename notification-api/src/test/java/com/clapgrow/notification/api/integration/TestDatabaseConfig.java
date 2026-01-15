package com.clapgrow.notification.api.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Test configuration to initialize database enum types before Hibernate creates tables.
 * This ensures PostgreSQL enum types exist before Hibernate tries to use them.
 * Uses DataSourceInitializer bean that implements InitializingBean to execute SQL scripts early.
 */
@TestConfiguration
@Order(Integer.MIN_VALUE) // Run as early as possible
public class TestDatabaseConfig {

    @Bean
    @DependsOn("dataSource")
    public DataSourceInitializer testDataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer() {
            @Override
            public void afterPropertiesSet() {
                // Execute the initialization immediately when bean is created
                super.afterPropertiesSet();
            }
        };
        initializer.setDataSource(dataSource);
        
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-test.sql"));
        populator.setContinueOnError(true); // Don't fail if enum types already exist
        populator.setSeparator(";");
        
        initializer.setDatabasePopulator(populator);
        initializer.setEnabled(true);
        
        // Force execution by calling afterPropertiesSet
        try {
            initializer.afterPropertiesSet();
        } catch (Exception e) {
            // Log but don't fail - enum types might already exist
            System.err.println("Warning: Could not initialize database enum types: " + e.getMessage());
        }
        
        return initializer;
    }
}
