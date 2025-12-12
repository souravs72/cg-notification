package com.clapgrow.notification.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class WhatsAppWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WhatsAppWorkerApplication.class, args);
    }
}

