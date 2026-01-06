package com.ai.infrastructure.behavior.it;

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

@SpringBootApplication(scanBasePackages = {
    "com.ai.infrastructure",
    "com.ai.infrastructure.behavior"
})
@Import({
    com.ai.infrastructure.config.AIInfrastructureAutoConfiguration.class,
    com.ai.infrastructure.behavior.config.BehaviorAIAutoConfiguration.class
})
@EntityScan(basePackages = {
    "com.ai.infrastructure.entity",
    "com.ai.infrastructure.behavior.entity"
})
@EnableJpaRepositories(basePackages = {
    "com.ai.infrastructure.repository",
    "com.ai.infrastructure.behavior.repository"
})
public class BehaviorIntegrationTestApp {
    public static void main(String[] args) {
        SpringApplication.run(BehaviorIntegrationTestApp.class, args);
    }

    /**
     * Test RAGProvider for behavior integration tests.
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
