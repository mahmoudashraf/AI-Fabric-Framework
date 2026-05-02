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
                var registration = registry.addMapping("/api/**")
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
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
}
