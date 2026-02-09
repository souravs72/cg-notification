package com.clapgrow.notification.api.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    
    private final AuthInterceptor authInterceptor;
    
    @Value("${file.upload.dir:uploads}")
    private String uploadDir;
    
    @Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;
    
    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String[] allowedMethods;
    
    @Value("${cors.allowed-headers:*}")
    private String[] allowedHeaders;
    
    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // AuthInterceptor only protects admin dashboard pages, not APIs
        // Admin API endpoints are excluded and handle authentication via @AdminApi annotation + AdminAuthAspect
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/admin/**")
                // Exclude admin API endpoints - they handle authentication via @AdminApi annotation + AdminAuthAspect
                // Exclude static resources and auth pages
                .excludePathPatterns(
                    "/admin/api/**",  // Admin APIs handled by controllers (e.g., /admin/api/**)
                    // Note: Sub-path APIs like /admin/campaigns/api/** are detected via @AdminApi annotation
                    "/auth/**", 
                    "/static/**", 
                    "/css/**", 
                    "/js/**", 
                    "/actuator/**", 
                    "/files/**"
                );
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Use absolute path to match FileStorageService
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        String resourceLocation = absolutePath.endsWith("/") ? absolutePath : absolutePath + "/";
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + resourceLocation);
    }
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Check if wildcard is used - if so, use allowedOriginPatterns instead
        boolean hasWildcard = Arrays.stream(allowedOrigins)
                .anyMatch(origin -> "*".equals(origin));
        
        var apiMapping = registry.addMapping("/api/**")
                .allowedMethods(allowedMethods)
                .allowedHeaders(allowedHeaders)
                .allowCredentials(true)
                .maxAge(maxAge);
        
        if (hasWildcard) {
            // Use allowedOriginPatterns for wildcard with credentials
            apiMapping.allowedOriginPatterns("*");
        } else {
            // Use allowedOrigins for specific origins
            apiMapping.allowedOrigins(allowedOrigins);
        }
        
        // Configure admin API CORS
        // Note: Sub-path APIs like /admin/campaigns/api/** are handled via @AdminApi annotation
        configureAdminCors(registry.addMapping("/admin/api/**"), hasWildcard);
    }
    
    /**
     * Helper method to configure CORS for admin API endpoints.
     * Reduces duplication and ensures consistent CORS settings.
     * 
     * @param registration CORS registration to configure
     * @param hasWildcard Whether wildcard origins are configured
     */
    private void configureAdminCors(CorsRegistration registration, boolean hasWildcard) {
        registration.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Admin-Key")
                .allowCredentials(true)
                .maxAge(maxAge);
        
        if (hasWildcard) {
            // Use allowedOriginPatterns for wildcard with credentials
            registration.allowedOriginPatterns("*");
        } else {
            // Use allowedOrigins for specific origins
            registration.allowedOrigins(allowedOrigins);
        }
    }
}

