package com.ai.behavior.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class RateLimitingConfiguration implements WebMvcConfigurer {

    private final BehaviorModuleProperties properties;

    @Bean
    public BehaviorRateLimitingInterceptor behaviorRateLimitingInterceptor() {
        return new BehaviorRateLimitingInterceptor(properties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(behaviorRateLimitingInterceptor());
    }
}
