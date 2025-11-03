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
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.privacy.AIDataPrivacyService;
import com.ai.infrastructure.filter.AIContentFilterService;
import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.rag.InMemoryVectorDatabaseService;
import com.ai.infrastructure.rag.LuceneVectorDatabaseService;
import com.ai.infrastructure.rag.PineconeVectorDatabaseService;
import com.ai.infrastructure.rag.SearchableEntityVectorDatabaseService;
import com.ai.infrastructure.search.VectorSearchService;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.embedding.ONNXEmbeddingProvider;
import com.ai.infrastructure.embedding.RestEmbeddingProvider;
import com.ai.infrastructure.embedding.OpenAIEmbeddingProvider;
import com.ai.infrastructure.cache.AICacheConfig;
import com.ai.infrastructure.vector.VectorDatabase;
import com.ai.infrastructure.vector.PineconeVectorDatabase;
import com.ai.infrastructure.config.AIConfigurationService;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.monitoring.AIHealthService;
import com.ai.infrastructure.api.AIAutoGeneratorService;
import com.ai.infrastructure.cache.AIIntelligentCacheService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Configuration
@EnableConfigurationProperties({AIProviderConfig.class, AIServiceConfig.class})
@ConditionalOnClass(AICapableAspect.class)
@EnableAspectJAutoProxy
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AIInfrastructureAutoConfiguration {
    
    public AIInfrastructureAutoConfiguration() {
        log.debug("AIInfrastructureAutoConfiguration instance created");
    }
    
    // EmbeddingProvider beans - selected based on configuration
    // The specific implementation is selected based on ai.providers.embedding-provider:
    // - ONNXEmbeddingProvider (default when not specified or set to "onnx")
    // - RestEmbeddingProvider (only when explicitly set to "rest")
    // - OpenAIEmbeddingProvider (only when explicitly set to "openai")
    // IMPORTANT: Define EmbeddingProvider BEFORE other beans that depend on it
    // IMPORTANT: ONNX is marked as @Primary to ensure it's used when multiple providers exist
    
    @Bean
    @org.springframework.context.annotation.Primary
    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "onnx", matchIfMissing = true)
    public EmbeddingProvider onnxEmbeddingProvider(AIProviderConfig config) {
        log.info("Creating ONNX Embedding Provider (primary/default)");
        log.info("ONNX will be used for all embedding generation - no fallback to other providers");
        ONNXEmbeddingProvider provider = new ONNXEmbeddingProvider(config);
        if (!provider.isAvailable()) {
            log.warn("WARNING: ONNX Embedding Provider is not available. Model file may be missing.");
            log.warn("Please ensure the ONNX model file exists at: {}", config.getOnnxModelPath());
        }
        return provider;
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "rest")
    @ConditionalOnMissingBean(name = "onnxEmbeddingProvider")
    public EmbeddingProvider restEmbeddingProvider(AIProviderConfig config) {
        log.info("Creating REST Embedding Provider");
        log.warn("ONNX is NOT being used. Using REST provider instead.");
        return new RestEmbeddingProvider(config);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "openai")
    @ConditionalOnMissingBean(name = "onnxEmbeddingProvider")
    public EmbeddingProvider openaiEmbeddingProvider(AIProviderConfig config) {
        log.info("Creating OpenAI Embedding Provider");
        log.warn("ONNX is NOT being used. Using OpenAI provider instead.");
        return new OpenAIEmbeddingProvider(config);
    }
    
    @Bean
    public AIEmbeddingService aiEmbeddingService(AIProviderConfig config, EmbeddingProvider embeddingProvider) {
        String providerName = embeddingProvider != null ? embeddingProvider.getProviderName() : "null";
        log.info("Creating AIEmbeddingService with provider: {}", providerName);
        
        // Validate that ONNX is being used (if that's what we want)
        if (!"onnx".equals(providerName)) {
            log.warn("WARNING: AIEmbeddingService is NOT using ONNX provider. Current provider: {}", providerName);
            log.warn("To use ONNX, ensure ai.providers.embedding-provider=onnx in configuration");
        } else {
            log.info("✅ AIEmbeddingService configured to use ONNX provider (no fallback to other providers)");
        }
        
        return new AIEmbeddingService(config, embeddingProvider);
    }
    
    @Bean
    public AICoreService aiCoreService(AIProviderConfig config, AIEmbeddingService embeddingService, AISearchService searchService) {
        return new AICoreService(config, embeddingService, searchService);
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
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "lucene", matchIfMissing = true)
    public VectorDatabaseService luceneVectorDatabaseService(
            AIProviderConfig config,
            AISearchableEntityRepository searchableEntityRepository,
            AIEntityConfigurationLoader configurationLoader) {
        VectorDatabaseService delegate = new LuceneVectorDatabaseService(config);
        return new SearchableEntityVectorDatabaseService(delegate, searchableEntityRepository, configurationLoader);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pinecone")
    public VectorDatabaseService pineconeVectorDatabaseService(
            AIProviderConfig config,
            AISearchableEntityRepository searchableEntityRepository,
            AIEntityConfigurationLoader configurationLoader) {
        VectorDatabaseService delegate = new PineconeVectorDatabaseService(config);
        return new SearchableEntityVectorDatabaseService(delegate, searchableEntityRepository, configurationLoader);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "memory")
    public VectorDatabaseService inMemoryVectorDatabaseService(
            AIProviderConfig config,
            AISearchableEntityRepository searchableEntityRepository,
            AIEntityConfigurationLoader configurationLoader) {
        VectorDatabaseService delegate = new InMemoryVectorDatabaseService(config);
        return new SearchableEntityVectorDatabaseService(delegate, searchableEntityRepository, configurationLoader);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AIEntityConfigurationLoader aiEntityConfigurationLoader(ResourceLoader resourceLoader) {
        return new AIEntityConfigurationLoader(resourceLoader);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public VectorManagementService vectorManagementService(VectorDatabaseService vectorDatabaseService) {
        return new VectorManagementService(vectorDatabaseService);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapabilityService aiCapabilityService(
            AIEmbeddingService embeddingService,
            AICoreService aiCoreService,
            AISearchableEntityRepository searchableEntityRepository,
            AIEntityConfigurationLoader entityConfigurationLoader) {
        AICapabilityService service = new AICapabilityService(embeddingService, aiCoreService, searchableEntityRepository, entityConfigurationLoader);
        return service;
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
    public VectorSearchService vectorSearchService(AIProviderConfig config, VectorDatabaseService vectorDatabaseService) {
        return new VectorSearchService(config, vectorDatabaseService);
    }
    
    @Bean
    public VectorDatabase vectorDatabase(AIProviderConfig config) {
        return new PineconeVectorDatabase(config);
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
    
            @Bean
            @ConditionalOnMissingBean
            public com.ai.infrastructure.service.BehaviorService behaviorService(com.ai.infrastructure.repository.BehaviorRepository behaviorRepository, AICapabilityService aiCapabilityService) {
                return new com.ai.infrastructure.service.BehaviorService(behaviorRepository, aiCapabilityService);
            }

    @Bean
    @ConditionalOnMissingBean
    public com.ai.infrastructure.service.AIInfrastructureProfileService aiInfrastructureProfileService(com.ai.infrastructure.repository.AIInfrastructureProfileRepository aiInfrastructureProfileRepository, AICapabilityService aiCapabilityService) {
        return new com.ai.infrastructure.service.AIInfrastructureProfileService(aiInfrastructureProfileRepository, aiCapabilityService);
    }

    @Bean
    @ConditionalOnMissingBean
    public com.ai.infrastructure.provider.ProviderConfig providerConfig(AIProviderConfig aiProviderConfig) {
        return com.ai.infrastructure.provider.ProviderConfig.builder()
                .providerName("openai")
                .apiKey(aiProviderConfig.getOpenaiApiKey())
                .baseUrl("https://api.openai.com/v1")
                .defaultModel(aiProviderConfig.getOpenaiModel())
                .defaultEmbeddingModel(aiProviderConfig.getOpenaiEmbeddingModel())
                .maxTokens(aiProviderConfig.getOpenaiMaxTokens())
                .temperature(aiProviderConfig.getOpenaiTemperature())
                .timeoutSeconds(aiProviderConfig.getOpenaiTimeout())
                .maxRetries(3)
                .retryDelayMs(1000L)
                .rateLimitPerMinute(60)
                .rateLimitPerDay(10000)
                .enabled(true)
                .priority(1)
                .build();
    }
}