package com.ai.infrastructure.rag.config;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.service.RAGService;
import com.ai.infrastructure.rag.spi.RAGProvider;
import com.ai.infrastructure.vector.VectorDatabase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for RAG (Retrieval-Augmented Generation) module.
 * 
 * <p>This configuration provides the RAGService which is a <strong>retrieval-only</strong>
 * service. It does NOT handle:</p>
 * <ul>
 *   <li>PII detection - handled by orchestrator pipeline</li>
 *   <li>LLM generation - handled by orchestrator pipeline</li>
 *   <li>Response sanitization - handled by orchestrator pipeline</li>
 * </ul>
 * 
 * <h2>Configuration Properties</h2>
 * <pre>{@code
 * ai.infrastructure.rag:
 *   enabled: true              # Enable RAG module
 *   default-limit: 10          # Default retrieval limit
 *   default-threshold: 0.7     # Default similarity threshold
 * }</pre>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @see RAGProvider
 * @see RAGService
 * @since 1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RAGProperties.class)
@ConditionalOnProperty(prefix = "ai.infrastructure.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RAGAutoConfiguration {
    
    private static final String LOG_RAG_SERVICE_CREATED = "RAGService created (retrieval-only mode)";
    
    /**
     * Creates the RAGService bean implementing RAGProvider SPI.
     * 
     * <p>This service performs retrieval operations only. The orchestrator
     * pipeline handles PII detection and LLM generation.</p>
     * 
     * @param config AI provider configuration
     * @param embeddingService embedding generation service
     * @param vectorDatabaseService vector database operations
     * @param vectorDatabase vector database interface
     * @param searchService search service
     * @return RAGService implementing RAGProvider
     */
    @Bean("ragService")
    @ConditionalOnMissingBean(RAGProvider.class)
    @ConditionalOnBean({
        AIProviderConfig.class,
        AIEmbeddingService.class,
        VectorDatabaseService.class,
        VectorDatabase.class,
        AISearchService.class
    })
    public RAGService ragService(
            AIProviderConfig config,
            AIEmbeddingService embeddingService,
            VectorDatabaseService vectorDatabaseService,
            VectorDatabase vectorDatabase,
            AISearchService searchService) {
        
        log.info(LOG_RAG_SERVICE_CREATED);
        
        return new RAGService(
            config,
            embeddingService,
            vectorDatabaseService,
            vectorDatabase,
            searchService
        );
    }
    
    /**
     * Register RAGService as ContentRetriever bean for orchestrator pipeline.
     * This allows the orchestrator to use RAGService via the ContentRetriever SPI.
     * Using @Primary ensures RAGService is preferred over any test ContentRetriever implementations.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    @ConditionalOnBean(RAGService.class)
    public com.ai.infrastructure.spi.ContentRetriever contentRetriever(RAGService ragService) {
        return ragService;
    }
}
