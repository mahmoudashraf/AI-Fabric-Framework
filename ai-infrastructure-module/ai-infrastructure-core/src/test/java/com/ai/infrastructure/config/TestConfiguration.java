package com.ai.infrastructure.config;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test Configuration for AI Infrastructure Core Unit Tests
 * 
 * This configuration provides a minimal Spring Boot context for unit testing
 * the AI infrastructure core module.
 */
@SpringBootApplication
@Import(AIInfrastructureAutoConfiguration.class)
@EntityScan(basePackages = "com.ai.infrastructure.entity")
@EnableJpaRepositories(basePackages = "com.ai.infrastructure.repository")
public class TestConfiguration {
}

