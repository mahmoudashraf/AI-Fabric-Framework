package com.ai.infrastructure.config;

import com.ai.infrastructure.provider.AIProviderManager;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * Test Configuration for AI Infrastructure Core Unit Tests
 * 
 * This configuration provides a minimal Spring Boot context for unit testing
 * the AI infrastructure core module.
 */
@SpringBootApplication(scanBasePackages = "com.ai.infrastructure")
@Import(AIInfrastructureAutoConfiguration.class)
@EntityScan(basePackages = "com.ai.infrastructure.entity")
@EnableJpaRepositories(basePackages = "com.ai.infrastructure.repository")
public class TestConfiguration {

        @Bean
        @Primary
    public AIProviderManager aiProviderManager() {
        return mock(AIProviderManager.class);
    }
}

