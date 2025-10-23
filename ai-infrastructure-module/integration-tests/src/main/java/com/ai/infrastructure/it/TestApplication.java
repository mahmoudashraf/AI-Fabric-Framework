package com.ai.infrastructure.it;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Test Application for AI Infrastructure Integration Tests
 * 
 * This is a minimal Spring Boot application used to test the AI Infrastructure
 * module in isolation. It provides a clean environment for integration testing
 * without dependencies on the main backend application.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootApplication
@Import(AIInfrastructureAutoConfiguration.class)
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.it.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.it.repository"
})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}