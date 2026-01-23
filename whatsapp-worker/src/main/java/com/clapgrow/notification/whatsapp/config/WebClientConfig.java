package com.clapgrow.notification.whatsapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * WebClient configuration for WhatsApp Worker.
 * Uses Reactor Netty HTTP client (not server) for WebClient.
 * WebFlux auto-configuration is explicitly excluded in WhatsAppWorkerApplication
 * to prevent reactive stack activation while allowing WebClient usage.
 */
@Configuration
public class WebClientConfig {
    
    @Value("${wasender.api.base-url:https://wasenderapi.com/api}")
    private String wasenderBaseUrl;

    @Bean
    public WebClient webClient() {
        // Use Reactor Netty HTTP client (not server) for WebClient
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));
        
        return WebClient.builder()
                .baseUrl(wasenderBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
    
    @Bean
    public WebClient.Builder webClientBuilder() {
        // Provide WebClient.Builder for services that need to build custom WebClient instances
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(30));
        
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024));
    }
}

