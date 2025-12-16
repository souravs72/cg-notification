package com.clapgrow.notification.email.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI emailWorkerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Email Worker API")
                        .description("Email Worker Service API Documentation. " +
                                "This service processes email notifications from Kafka and sends them via SendGrid. " +
                                "It exposes actuator endpoints for health checks and metrics.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ClapGrow")
                                .email("support@clapgrow.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local Development Server"),
                        new Server().url("https://email-worker.notification.example.com").description("Production Server")
                ));
    }
}

