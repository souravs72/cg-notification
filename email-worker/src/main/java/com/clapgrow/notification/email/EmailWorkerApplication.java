package com.clapgrow.notification.email;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
@SpringBootApplication
@EnableJpaAuditing
public class EmailWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmailWorkerApplication.class, args);
    }
}

