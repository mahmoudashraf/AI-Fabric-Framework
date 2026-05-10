package com.ai.fabric.platform.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer webMvcConfigurer(PlatformCorsProperties corsProperties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                if (corsProperties == null
                    || (corsProperties.getAllowedOrigins().isEmpty()
                    && corsProperties.getAllowedOriginPatterns().isEmpty())) {
                    return;
                }
                validateCorsConfiguration(corsProperties);
                var registration = registry.addMapping("/api/**")
                    // PATCH is required by Platform-managed deployment repair/reconcile APIs; origin scope stays property-driven.
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders(corsProperties.getAllowedHeaders().toArray(String[]::new))
                    .allowCredentials(corsProperties.isAllowCredentials());

                if (!corsProperties.getAllowedOrigins().isEmpty()) {
                    registration.allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
                }
                if (!corsProperties.getAllowedOriginPatterns().isEmpty()) {
                    registration.allowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new));
                }
            }
        };
    }

    private void validateCorsConfiguration(PlatformCorsProperties corsProperties) {
        if (!corsProperties.isAllowCredentials()) {
            return;
        }
        boolean wildcardOrigin = corsProperties.getAllowedOrigins().stream()
            .anyMatch("*"::equals)
            || corsProperties.getAllowedOriginPatterns().stream()
            .anyMatch("*"::equals);
        if (wildcardOrigin) {
            throw new IllegalStateException("CORS credentials cannot be enabled with wildcard origins.");
        }
        if (corsProperties.getAllowedHeaders().isEmpty()
            || corsProperties.getAllowedHeaders().stream().anyMatch("*"::equals)) {
            throw new IllegalStateException("CORS credentials require an explicit allowed header list.");
        }
    }
}
