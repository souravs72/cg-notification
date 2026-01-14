package com.clapgrow.notification.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Configuration
public class JacksonConfig {
    
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        
        // Configure JavaTimeModule to serialize LocalDateTime as UTC ISO-8601 strings
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        
        // Custom serializer that treats LocalDateTime as UTC and adds 'Z' suffix
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        ) {
            @Override
            public void serialize(LocalDateTime value, com.fasterxml.jackson.core.JsonGenerator gen, 
                                 com.fasterxml.jackson.databind.SerializerProvider provider) 
                    throws java.io.IOException {
                // Treat LocalDateTime as UTC and serialize with 'Z' suffix
                gen.writeString(value.atZone(ZoneOffset.UTC).format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                ));
            }
        });
        
        mapper.registerModule(javaTimeModule);
        
        // Configure to write dates as ISO-8601 strings (not timestamps)
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        return mapper;
    }
}

