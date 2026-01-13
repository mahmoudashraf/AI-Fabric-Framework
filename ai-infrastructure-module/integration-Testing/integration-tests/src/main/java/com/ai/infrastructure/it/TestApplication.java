package com.ai.infrastructure.it;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.indexing.config.AIIndexingAutoConfiguration;
import com.ai.infrastructure.rag.config.RAGAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

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
@ComponentScan(
    basePackages = "com.ai.infrastructure.it",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = {
            "com\\.ai\\.infrastructure\\.it\\.storage\\..*"
        }
    )
)
@Import({AIInfrastructureAutoConfiguration.class, AIIndexingAutoConfiguration.class, RAGAutoConfiguration.class})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public EntityAccessPolicy testEntityAccessPolicy() {
        return (userId, entity) -> true;
    }

    @Bean
    public ComplianceCheckProvider testComplianceCheckProvider() {
        return request -> ComplianceCheckResult.builder()
            .compliant(true)
            .details("Test compliance provider approval")
            .build();
    }
}
