package com.easyluxury.ai.config;

import com.ai.infrastructure.config.AIConfigurationService;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.search.VectorSearchService;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.monitoring.AIMetricsService;
import com.ai.infrastructure.rag.InMemoryVectorDatabaseService;
import com.ai.infrastructure.processor.AICapableProcessor;
import com.ai.infrastructure.processor.EmbeddingProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Test AI Configuration
 * 
 * This configuration provides mock implementations of AI services for testing.
 * It ensures that all AI infrastructure beans are properly configured in the test context.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@TestConfiguration
@Profile("test")
public class TestAIConfiguration {
    
    @Bean
    @Primary
    public AIConfigurationService aiConfigurationService(AIServiceConfig serviceConfig) {
        log.info("Creating AIConfigurationService for test context");
        return new AIConfigurationService(serviceConfig);
    }
    
    @Bean
    @Primary
    public AIHealthIndicator aiHealthIndicator(AIConfigurationService configurationService, AIServiceConfig serviceConfig) {
        log.info("Creating AIHealthIndicator for test context");
        return new AIHealthIndicator(configurationService, serviceConfig);
    }
    
    @Bean
    @Primary
    public AIMetricsService aiMetricsService() {
        log.info("Creating AIMetricsService for test context");
        return new AIMetricsService();
    }
    
    @Bean
    @Primary
    public VectorSearchService vectorSearchService(AIProviderConfig config) {
        log.info("Creating VectorSearchService for test context");
        return new VectorSearchService(config);
    }
    
    @Bean
    @Primary
    public VectorDatabaseService vectorDatabaseService(AIProviderConfig config) {
        log.info("Creating VectorDatabaseService for test context");
        return new InMemoryVectorDatabaseService(config);
    }
    
    @Bean
    @Primary
    public AIEmbeddingService aiEmbeddingService(AIProviderConfig config) {
        log.info("Creating AIEmbeddingService for test context");
        return new AIEmbeddingService(config);
    }
    
    @Bean
    @Primary
    public AISearchService aiSearchService(AIProviderConfig config, VectorSearchService vectorSearchService) {
        log.info("Creating AISearchService for test context");
        return new AISearchService(config, vectorSearchService);
    }
    
    @Bean
    @Primary
    public AICoreService aiCoreService(AIProviderConfig config, AIEmbeddingService embeddingService, AISearchService searchService) {
        log.info("Creating AICoreService for test context");
        return new AICoreService(config, embeddingService, searchService);
    }
    
    @Bean
    @Primary
    public RAGService ragService(AIProviderConfig config, AIEmbeddingService embeddingService, VectorDatabaseService vectorDatabaseService, AISearchService searchService) {
        log.info("Creating RAGService for test context");
        return new RAGService(config, embeddingService, vectorDatabaseService, null, searchService);
    }
    
    @Bean
    @Primary
    public AICapableProcessor aiCapableProcessor() {
        log.info("Creating AICapableProcessor for test context");
        return new AICapableProcessor();
    }
    
    @Bean
    @Primary
    public EmbeddingProcessor embeddingProcessor(AIProviderConfig config) {
        log.info("Creating EmbeddingProcessor for test context");
        return new EmbeddingProcessor(config);
    }
}
