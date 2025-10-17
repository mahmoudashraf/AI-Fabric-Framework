package com.ai.infrastructure.config;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.processor.AICapableProcessor;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for AI Infrastructure module
 * 
 * This class automatically configures all AI infrastructure beans when the module
 * is included in a Spring Boot application.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties(AIProviderConfig.class)
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AIInfrastructureAutoConfiguration {
    
    @Bean
    public AICoreService aiCoreService(AIProviderConfig config) {
        return new AICoreService(config, aiEmbeddingService(config), aiSearchService(config));
    }
    
    @Bean
    public AIEmbeddingService aiEmbeddingService(AIProviderConfig config) {
        return new AIEmbeddingService(config);
    }
    
    @Bean
    public AISearchService aiSearchService(AIProviderConfig config) {
        return new AISearchService(config);
    }
    
    @Bean
    public RAGService ragService(AIProviderConfig config, AIEmbeddingService embeddingService, VectorDatabaseService vectorDatabaseService) {
        return new RAGService(config, embeddingService, vectorDatabaseService);
    }
    
    @Bean
    public VectorDatabaseService vectorDatabaseService(AIProviderConfig config) {
        return new VectorDatabaseService(config);
    }
    
    @Bean
    public AICapableProcessor aiCapableProcessor() {
        return new AICapableProcessor();
    }
}