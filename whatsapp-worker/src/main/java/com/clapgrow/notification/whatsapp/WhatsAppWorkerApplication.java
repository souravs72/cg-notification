package com.clapgrow.notification.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(exclude = {
    WebFluxAutoConfiguration.class,  // Explicitly exclude WebFlux auto-configuration (worker doesn't need reactive server)
    ReactiveWebServerFactoryAutoConfiguration.class  // Exclude reactive web server factory (prevents Netty server startup)
})
@EnableKafka
public class WhatsAppWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsAppWorkerApplication.class, args);
    }
}

