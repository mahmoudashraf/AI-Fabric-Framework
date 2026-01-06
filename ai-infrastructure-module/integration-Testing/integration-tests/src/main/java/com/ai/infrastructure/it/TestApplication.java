package com.ai.infrastructure.it;

import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.compliance.policy.ComplianceCheckResult;
import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.spi.RAGProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;

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
@SpringBootApplication(scanBasePackages = {"com.ai.infrastructure", "com.ai.infrastructure.it"})
@Import(AIInfrastructureAutoConfiguration.class)
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.it.entity",
    "com.ai.infrastructure.migration.domain"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.it.repository",
    "com.ai.infrastructure.migration.repository"
})
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

    /**
     * Test RAGProvider for integration tests.
     */
    @Bean
    @Primary
    public RAGProvider testRAGProvider() {
        return new RAGProvider() {
            @Override
            public RAGResponse performRag(RAGRequest request) {
                return RAGResponse.builder()
                    .response("Test RAG response")
                    .documents(List.of())
                    .success(true)
                    .build();
            }

            @Override
            public RAGResponse performRAGQuery(RAGRequest request) {
                return performRag(request);
            }

            @Override
            public void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata) {
                // No-op for tests
            }

            @Override
            public void removeContent(String entityType, String entityId) {
                // No-op for tests
            }

            @Override
            public Map<String, Object> getStatistics() {
                return Map.of("provider", "test");
            }

            @Override
            public String getProviderName() {
                return "test-rag-provider";
            }
        };
    }
}
