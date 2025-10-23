package com.ai.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Provider Configuration
 * 
 * Configuration for AI provider dependencies and beans.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Configuration
public class ProviderConfiguration {
    
    /**
     * RestTemplate bean for HTTP calls to AI providers
     * 
     * @return RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}