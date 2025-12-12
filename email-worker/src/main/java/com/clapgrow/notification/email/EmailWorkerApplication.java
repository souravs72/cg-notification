package com.clapgrow.notification.email;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class EmailWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmailWorkerApplication.class, args);
    }
}

