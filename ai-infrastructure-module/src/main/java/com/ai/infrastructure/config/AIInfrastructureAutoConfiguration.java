package com.ai.infrastructure.config;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.processor.AICapableProcessor;
import com.ai.infrastructure.processor.EmbeddingProcessor;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.search.VectorSearchService;
import com.ai.infrastructure.cache.AICacheConfig;
import com.ai.infrastructure.vector.VectorDatabase;
import com.ai.infrastructure.vector.PineconeVectorDatabase;
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
@EnableConfigurationProperties({AIProviderConfig.class, AIServiceConfig.class})
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AIInfrastructureAutoConfiguration {
    
    @Bean
    public AICoreService aiCoreService(AIProviderConfig config, AIEmbeddingService embeddingService, AISearchService searchService) {
        return new AICoreService(config, embeddingService, searchService);
    }
    
    @Bean
    public AIEmbeddingService aiEmbeddingService(AIProviderConfig config) {
        return new AIEmbeddingService(config);
    }
    
    @Bean
    public AISearchService aiSearchService(AIProviderConfig config, VectorSearchService vectorSearchService) {
        return new AISearchService(config, vectorSearchService);
    }
    
    @Bean
    public RAGService ragService(AIProviderConfig config, AIEmbeddingService embeddingService, VectorDatabaseService vectorDatabaseService, VectorDatabase vectorDatabase) {
        return new RAGService(config, embeddingService, vectorDatabaseService, vectorDatabase);
    }
    
    @Bean
    public VectorDatabaseService vectorDatabaseService(AIProviderConfig config) {
        return new VectorDatabaseService(config);
    }
    
    @Bean
    public AICapableProcessor aiCapableProcessor() {
        return new AICapableProcessor();
    }
    
    @Bean
    public EmbeddingProcessor embeddingProcessor(AIProviderConfig config) {
        return new EmbeddingProcessor(config);
    }
    
    @Bean
    public VectorSearchService vectorSearchService(AIProviderConfig config) {
        return new VectorSearchService(config);
    }
    
    @Bean
    public VectorDatabase vectorDatabase(AIProviderConfig config) {
        return new PineconeVectorDatabase(config);
    }
}