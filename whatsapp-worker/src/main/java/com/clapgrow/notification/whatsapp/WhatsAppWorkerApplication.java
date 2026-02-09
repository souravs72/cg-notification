package com.clapgrow.notification.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;

@SpringBootApplication(exclude = {
    WebFluxAutoConfiguration.class,  // Explicitly exclude WebFlux auto-configuration (worker doesn't need reactive server)
    ReactiveWebServerFactoryAutoConfiguration.class  // Exclude reactive web server factory (prevents Netty server startup)
})
public class WhatsAppWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsAppWorkerApplication.class, args);
    }
}

