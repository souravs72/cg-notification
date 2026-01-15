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
 * Uses DataSourceInitializer bean that runs before EntityManagerFactory initialization.
 */
@TestConfiguration
@Order(Integer.MIN_VALUE) // Run as early as possible
public class TestDatabaseConfig {

    @Bean
    @DependsOn("dataSource")
    public DataSourceInitializer testDataSourceInitializer(DataSource dataSource) {
        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-test.sql"));
        populator.setContinueOnError(true); // Don't fail if enum types already exist
        populator.setSeparator(";");
        
        initializer.setDatabasePopulator(populator);
        initializer.setEnabled(true);
        
        return initializer;
    }
}
