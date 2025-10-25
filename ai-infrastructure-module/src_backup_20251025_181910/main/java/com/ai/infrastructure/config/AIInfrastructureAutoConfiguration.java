package com.ai.infrastructure.config;

import com.ai.infrastructure.aspect.AICapableAspect;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.processor.AICapableProcessor;
import com.ai.infrastructure.processor.EmbeddingProcessor;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.privacy.AIDataPrivacyService;
import com.ai.infrastructure.filter.AIContentFilterService;
import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.rag.LuceneVectorDatabaseService;
import com.ai.infrastructure.rag.PineconeVectorDatabaseService;
import com.ai.infrastructure.rag.InMemoryVectorDatabaseService;
import com.ai.infrastructure.search.VectorSearchService;
import com.ai.infrastructure.cache.AICacheConfig;
import com.ai.infrastructure.vector.VectorDatabase;
import com.ai.infrastructure.vector.PineconeVectorDatabase;
import com.ai.infrastructure.config.AIConfigurationService;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.monitoring.AIHealthService;
import com.ai.infrastructure.api.AIAutoGeneratorService;
import com.ai.infrastructure.cache.AIIntelligentCacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ResourceLoader;

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
@ConditionalOnClass(AICapableAspect.class)
@EnableAspectJAutoProxy
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
    public RAGService ragService(AIProviderConfig config, AIEmbeddingService embeddingService, VectorDatabaseService vectorDatabaseService, VectorDatabase vectorDatabase, AISearchService searchService) {
        return new RAGService(config, embeddingService, vectorDatabaseService, vectorDatabase, searchService);
    }
    
    @Bean
    public AdvancedRAGService advancedRAGService(AISearchService aiSearchService, AIEmbeddingService aiEmbeddingService, AICoreService aiCoreService, RAGService ragService) {
        return new AdvancedRAGService(aiSearchService, aiEmbeddingService, aiCoreService, ragService);
    }
    
    @Bean
    public AISecurityService aiSecurityService(AICoreService aiCoreService) {
        return new AISecurityService(aiCoreService);
    }
    
    @Bean
    public AIComplianceService aiComplianceService(AICoreService aiCoreService) {
        return new AIComplianceService(aiCoreService);
    }
    
    @Bean
    public AIAuditService aiAuditService(AICoreService aiCoreService) {
        return new AIAuditService(aiCoreService);
    }
    
    @Bean
    public AIDataPrivacyService aiDataPrivacyService(AICoreService aiCoreService) {
        return new AIDataPrivacyService(aiCoreService);
    }
    
    @Bean
    public AIContentFilterService aiContentFilterService(AICoreService aiCoreService) {
        return new AIContentFilterService(aiCoreService);
    }
    
    @Bean
    public AIAccessControlService aiAccessControlService(AICoreService aiCoreService) {
        return new AIAccessControlService(aiCoreService);
    }
    
    // VectorDatabaseService is now an interface with multiple implementations
    // The specific implementation is selected based on configuration:
    // - LuceneVectorDatabaseService (default)
    // - PineconeVectorDatabaseService
    // - InMemoryVectorDatabaseService
    
    @Bean
    @ConditionalOnMissingBean
    public AIEntityConfigurationLoader aiEntityConfigurationLoader(ResourceLoader resourceLoader) {
        return new AIEntityConfigurationLoader(resourceLoader);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapabilityService aiCapabilityService(
            AIEmbeddingService embeddingService,
            AICoreService aiCoreService,
            AISearchableEntityRepository searchableEntityRepository) {
        return new AICapabilityService(embeddingService, aiCoreService, searchableEntityRepository);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapableAspect aiCapableAspect(
            AIEntityConfigurationLoader configLoader,
            AICapabilityService aiCapabilityService) {
        return new AICapableAspect(configLoader, aiCapabilityService);
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
    
    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "lucene", matchIfMissing = true)
    public VectorDatabaseService luceneVectorDatabaseService(AIProviderConfig config) {
        return new LuceneVectorDatabaseService(config);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pinecone")
    public VectorDatabaseService pineconeVectorDatabaseService(AIProviderConfig config) {
        return new PineconeVectorDatabaseService(config);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "memory")
    public VectorDatabaseService inMemoryVectorDatabaseService(AIProviderConfig config) {
        return new InMemoryVectorDatabaseService(config);
    }
    
    @Bean
    public AIConfigurationService aiConfigurationService(AIServiceConfig serviceConfig) {
        return new AIConfigurationService(serviceConfig);
    }
    
    @Bean
    public AIHealthIndicator aiHealthIndicator(AIConfigurationService configurationService, AIServiceConfig serviceConfig) {
        return new AIHealthIndicator(configurationService, serviceConfig);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.service.auto-generator.enabled", havingValue = "true", matchIfMissing = false)
    public AIAutoGeneratorService aiAutoGeneratorService(AICoreService aiCoreService) {
        // TODO: Implement concrete implementation
        return null;
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.service.intelligent-cache.enabled", havingValue = "true", matchIfMissing = false)
    public AIIntelligentCacheService aiIntelligentCacheService() {
        // TODO: Implement concrete implementation
        return null;
    }
}