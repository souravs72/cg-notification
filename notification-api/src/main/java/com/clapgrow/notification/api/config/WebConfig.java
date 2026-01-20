package com.clapgrow.notification.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final AuthInterceptor authInterceptor;
    
    @Value("${file.upload.dir:uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/auth/**", "/static/**", "/css/**", "/js/**", "/actuator/**", "/files/**");
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Use absolute path to match FileStorageService
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        String resourceLocation = absolutePath.endsWith("/") ? absolutePath : absolutePath + "/";
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + resourceLocation);
    }
}

