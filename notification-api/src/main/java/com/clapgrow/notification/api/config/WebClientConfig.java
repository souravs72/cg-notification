package com.clapgrow.notification.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient configuration for MVC application.
 * Uses Reactor Netty HTTP client (not server) for WebClient.
 * WebFlux auto-configuration is explicitly excluded in NotificationApiApplication
 * to prevent reactive stack activation while allowing WebClient usage.
 */
@Configuration
public class WebClientConfig {
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Use Reactor Netty HTTP client (not server) for WebClient
        // This provides WebClient functionality without starting a reactive server
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));
        
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}

