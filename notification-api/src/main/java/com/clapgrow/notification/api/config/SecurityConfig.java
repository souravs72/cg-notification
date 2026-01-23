package com.clapgrow.notification.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Value("${actuator.enabled:false}")
    private boolean actuatorEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CSRF protection for form-based auth (login/register)
            // Disable CSRF for API endpoints and admin routes:
            // - /api/** uses API key authentication (no CSRF needed)
            // - /admin/** includes both APIs (API key auth) and dashboard pages (protected by AuthInterceptor)
            //   AdminAuthAspect enforces authentication for API endpoints, AuthInterceptor for dashboard pages
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/admin/**")
            )
            .formLogin(form -> form.disable()) // Disable default form login (using custom auth via AuthInterceptor)
            .httpBasic(basic -> basic.disable()) // Disable HTTP basic authentication
            .logout(logout -> logout.disable()) // Disable default logout (using custom logout)
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000))
                .referrerPolicy(policy -> policy
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .xssProtection(xss -> xss
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)))
            .authorizeHttpRequests(auth -> auth
                // Allow public access to authentication endpoints
                .requestMatchers("/auth/**").permitAll()
                // Allow public access to static resources
                .requestMatchers("/static/**", "/css/**", "/js/**", "/files/**").permitAll()
                // Conditionally allow actuator endpoints (should be secured in production)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/actuator/**").access((authentication, context) -> 
                    new AuthorizationDecision(actuatorEnabled))
                // Allow public access to API docs (should be disabled in production via SWAGGER_UI_ENABLED=false)
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Allow public access to root redirect
                .requestMatchers("/", "/favicon.ico").permitAll()
                // Allow public access to API endpoints (they use API key authentication)
                .requestMatchers("/api/**").permitAll()
                // Admin API endpoints are excluded from session checks;
                // authentication is enforced centrally by AdminAuthAspect
                // Note: Annotation-based detection (@AdminApi) handles sub-path APIs like /admin/campaigns/api/**
                .requestMatchers("/admin/api/**").permitAll()
                // Admin dashboard pages are permitted here;
                // AuthInterceptor enforces session authentication for non-API admin routes
                // This allows Spring Security to pass requests through, then AuthInterceptor checks sessions
                .requestMatchers("/admin/**").permitAll()
                // ✅ SECURITY: Deny-by-default prevents accidental exposure of new endpoints
                // New endpoints must be explicitly added above or will be denied
                // This acts as a "seatbelt" for future developers
                .anyRequest().denyAll()
            );
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}


