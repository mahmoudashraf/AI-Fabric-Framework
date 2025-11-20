package com.ai.behavior.integration;

import com.ai.behavior.config.BehaviorModuleConfiguration;
import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.policy.BehaviorAnalysisPolicy;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.rag.RAGService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.ai.behavior")
@EnableJpaRepositories(basePackages = {"com.ai.behavior.repository", "com.ai.behavior.storage"})
@EntityScan(basePackages = "com.ai.behavior")
@EnableConfigurationProperties(BehaviorModuleProperties.class)
@Import({
    BehaviorModuleConfiguration.class,
    TestBehaviorApplication.TestBehaviorMocks.class,
    TestBehaviorApplication.TestBehaviorPolicyMock.class
})
public class TestBehaviorApplication {

    @Configuration
    static class TestBehaviorMocks {
        @Bean
        AICoreService aiCoreService() {
            AICoreService mockService = mock(AICoreService.class);
            when(mockService.generateEmbedding(any())).thenReturn(
                AIEmbeddingResponse.builder()
                    .embedding(List.of(0.1d, 0.2d, 0.3d))
                    .model("test-model")
                    .dimensions(3)
                    .processingTimeMs(1L)
                    .requestId("test")
                    .build()
            );
            return mockService;
        }

        @Bean
        RAGService ragService() {
            return mock(RAGService.class);
        }

        @Bean
        RAGOrchestrator ragOrchestrator() {
            RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
            when(orchestrator.orchestrate(anyString(), any())).thenReturn(
                OrchestrationResult.builder()
                    .success(true)
                    .type(OrchestrationResultType.INFORMATION_PROVIDED)
                    .message("stubbed")
                    .metadata(Map.of())
                    .data(Map.of())
                    .build()
            );
            return orchestrator;
        }

        @Bean
        PIIDetectionService piiDetectionService() {
            PIIDetectionService service = mock(PIIDetectionService.class);
            when(service.detectAndProcess(anyString())).thenReturn(
                PIIDetectionResult.builder()
                    .piiDetected(false)
                    .processedQuery("")
                    .build()
            );
            return service;
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "ai.behavior.test.mock-policy", havingValue = "true", matchIfMissing = true)
    static class TestBehaviorPolicyMock {
        @Bean
        BehaviorAnalysisPolicy behaviorAnalysisPolicy() {
            return new BehaviorAnalysisPolicy() {
                @Override
                public List<String> detectPatterns(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    return List.of("test_pattern");
                }

                @Override
                public String determineSegment(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    return "test_segment";
                }

                @Override
                public List<String> generateRecommendations(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    return List.of("recommendation");
                }

                @Override
                public double calculateConfidence(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
                    return 0.9;
                }
            };
        }
    }
}
