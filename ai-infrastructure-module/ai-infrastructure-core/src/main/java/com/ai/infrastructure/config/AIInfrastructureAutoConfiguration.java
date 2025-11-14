package com.ai.infrastructure.config;

import com.ai.infrastructure.aspect.AICapableAspect;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.processor.AICapableProcessor;
import com.ai.infrastructure.processor.EmbeddingProcessor;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.behavior.BehaviorRetentionService;
import com.ai.infrastructure.behavior.policy.BehaviorRetentionPolicyProvider;
import com.ai.infrastructure.privacy.AIDataPrivacyService;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.filter.AIContentFilterService;
import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.deletion.UserDataDeletionService;
import com.ai.infrastructure.deletion.policy.UserDataDeletionProvider;
import com.ai.infrastructure.search.VectorSearchService;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.cache.AICacheConfig;
import com.ai.infrastructure.vector.VectorDatabase;
import com.ai.infrastructure.vector.VectorDatabaseServiceAdapter;
import com.ai.infrastructure.config.AIConfigurationService;
import com.ai.infrastructure.config.IntentHistoryProperties;
import com.ai.infrastructure.config.ResponseSanitizationProperties;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.monitoring.AIHealthService;
import com.ai.infrastructure.api.AIAutoGeneratorService;
import com.ai.infrastructure.api.DefaultAIAutoGeneratorService;
import com.ai.infrastructure.cache.AIIntelligentCacheService;
import com.ai.infrastructure.cache.CacheConfig;
import com.ai.infrastructure.cache.DefaultAIIntelligentCacheService;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.repository.BehaviorRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.io.ResourceLoader;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    @EnableConfigurationProperties({
        AIProviderConfig.class,
        AIServiceConfig.class,
        PIIDetectionProperties.class,
        SmartSuggestionsProperties.class,
        ResponseSanitizationProperties.class,
        IntentHistoryProperties.class,
        SecurityProperties.class
    })
@Import(ProviderConfiguration.class)
@ConditionalOnClass(AICapableAspect.class)
@EnableAspectJAutoProxy
@EnableScheduling
@ConditionalOnProperty(prefix = "ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AIInfrastructureAutoConfiguration {
    
    public AIInfrastructureAutoConfiguration() {
        log.debug("AIInfrastructureAutoConfiguration instance created");
    }
    
    // EmbeddingProvider beans - selected based on configuration
    // Embedding provider implementations are supplied by dedicated modules

    @Bean
    public AIEmbeddingService aiEmbeddingService(AIProviderConfig config,
                                                 List<EmbeddingProvider> embeddingProviders,
                                                 ObjectProvider<CacheManager> cacheManagerProvider,
                                                 @Qualifier("onnxFallbackEmbeddingProvider") ObjectProvider<EmbeddingProvider> fallbackProvider) {
        String requestedProvider = config.getEmbeddingProvider() != null
            ? config.getEmbeddingProvider().toLowerCase()
            : null;

        EmbeddingProvider selectedProvider = embeddingProviders.stream()
            .filter(provider -> provider != null && provider.getProviderName() != null)
            .filter(provider -> requestedProvider == null
                || provider.getProviderName().equalsIgnoreCase(requestedProvider))
            .findFirst()
            .orElseGet(() -> embeddingProviders.isEmpty() ? null : embeddingProviders.get(0));

        String providerName = selectedProvider != null ? selectedProvider.getProviderName() : "null";
        log.info("Creating AIEmbeddingService with embedding provider '{}' (requested '{}')",
            providerName,
            requestedProvider != null ? requestedProvider : "unspecified");

        if (selectedProvider == null) {
            log.warn("No embedding provider matched request '{}'. Available providers: {}",
                requestedProvider,
                embeddingProviders.stream()
                    .filter(Objects::nonNull)
                    .map(EmbeddingProvider::getProviderName)
                    .collect(Collectors.joining(", ")));
        }
        
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable(NoOpCacheManager::new);
        EmbeddingProvider fallbackEmbeddingProvider = fallbackProvider != null ? fallbackProvider.getIfAvailable() : null;
        return new AIEmbeddingService(config, selectedProvider, cacheManager, fallbackEmbeddingProvider);
    }
    
    @Bean
    public AISearchService aiSearchService(AIProviderConfig config,
                                           VectorSearchService vectorSearchService,
                                           VectorManagementService vectorManagementService) {
        return new AISearchService(config, vectorSearchService, vectorManagementService);
    }
    
    @Bean
    public AdvancedRAGService advancedRAGService(AISearchService aiSearchService, AIEmbeddingService aiEmbeddingService, AICoreService aiCoreService, RAGService ragService) {
        return new AdvancedRAGService(aiSearchService, aiEmbeddingService, aiCoreService, ragService);
    }
    
    @Bean
    public AISecurityService aiSecurityService(PIIDetectionService piiDetectionService,
                                               AuditService auditService,
                                               Clock clock,
                                               SecurityProperties securityProperties) {
        return new AISecurityService(piiDetectionService, auditService, clock, securityProperties);
    }
    
    @Bean
    public AIComplianceService aiComplianceService(AuditService auditService,
                                                   Clock clock,
                                                   ObjectProvider<ComplianceCheckProvider> complianceProvider) {
        return new AIComplianceService(auditService, clock, complianceProvider.getIfAvailable());
    }
    
    @Bean
    public BehaviorRetentionService behaviorRetentionService(BehaviorRepository behaviorRepository,
                                                             AuditService auditService,
                                                             Clock clock,
                                                             ObjectProvider<BehaviorRetentionPolicyProvider> retentionPolicyProvider) {
        return new BehaviorRetentionService(
            behaviorRepository,
            retentionPolicyProvider.getIfAvailable(),
            auditService,
            clock
        );
    }

    @Bean
    public UserDataDeletionService userDataDeletionService(BehaviorRepository behaviorRepository,
                                                           AISearchableEntityRepository searchableEntityRepository,
                                                           VectorDatabaseService vectorDatabaseService,
                                                           AIAuditService aiAuditService,
                                                           AuditService auditService,
                                                           Clock clock,
                                                           ObjectProvider<UserDataDeletionProvider> deletionProvider) {
        return new UserDataDeletionService(
            behaviorRepository,
            searchableEntityRepository,
            vectorDatabaseService,
            aiAuditService,
            auditService,
            clock,
            deletionProvider.getIfAvailable()
        );
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
    @ConditionalOnMissingBean
    public PIIDetectionService piiDetectionService(PIIDetectionProperties properties) {
        return new PIIDetectionService(properties);
    }
    
    @Bean
    public AIContentFilterService aiContentFilterService(AICoreService aiCoreService) {
        return new AIContentFilterService(aiCoreService);
    }
    
    @Bean
    public AIAccessControlService aiAccessControlService(AuditService auditService,
                                                         Clock clock,
                                                         ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider) {
        return new AIAccessControlService(
            auditService,
            clock,
            entityAccessPolicyProvider.getIfAvailable()
        );
    }
    
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock systemClock() {
        return Clock.systemUTC();
    }
    
    // Vector database implementations are provided by dedicated vector modules

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
            AIEntityConfigurationLoader entityConfigurationLoader,
            VectorManagementService vectorManagementService) {
        return new AICapabilityService(embeddingService, aiCoreService, searchableEntityRepository, entityConfigurationLoader, vectorManagementService);
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
    public VectorSearchService vectorSearchService(AIProviderConfig config,
                                                  VectorDatabaseService vectorDatabaseService,
                                                  ObjectProvider<CacheManager> cacheManagerProvider) {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable(NoOpCacheManager::new);
        return new VectorSearchService(config, vectorDatabaseService, cacheManager);
    }
    
    @Bean
    @ConditionalOnMissingBean(VectorDatabase.class)
    public VectorDatabase vectorDatabase(VectorDatabaseService vectorDatabaseService) {
        return new VectorDatabaseServiceAdapter(vectorDatabaseService);
    }
    
    @Bean
    public AIConfigurationService aiConfigurationService(AIProviderConfig providerConfig, AIServiceConfig serviceConfig) {
        return new AIConfigurationService(providerConfig, serviceConfig);
    }
    
    @Bean
    public AIHealthIndicator aiHealthIndicator(AIConfigurationService configurationService, AIServiceConfig serviceConfig) {
        return new AIHealthIndicator(configurationService, serviceConfig);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.service.auto-generator.enabled", havingValue = "true", matchIfMissing = false)
    public AIAutoGeneratorService aiAutoGeneratorService(AICoreService aiCoreService, AIServiceConfig serviceConfig) {
        return new DefaultAIAutoGeneratorService(aiCoreService, serviceConfig);
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.service.intelligent-cache.enabled", havingValue = "true", matchIfMissing = false)
    public AIIntelligentCacheService aiIntelligentCacheService(AIServiceConfig serviceConfig) {
        return new DefaultAIIntelligentCacheService(resolveCacheConfig(serviceConfig));
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

    private CacheConfig resolveCacheConfig(AIServiceConfig serviceConfig) {
        AIServiceConfig.CacheConfig original = serviceConfig != null ? serviceConfig.getCache() : null;

        boolean enabled = serviceConfig == null || serviceConfig.isCachingEnabled();
        if (original != null && original.getEnabled() != null) {
            enabled = enabled && original.getEnabled();
        }

        Duration defaultTtl = original != null && original.getDefaultTtl() != null
            ? original.getDefaultTtl()
            : Duration.ofMinutes(10);

        Long maxSize = original != null && original.getMaxSize() != null
            ? original.getMaxSize()
            : 10_000L;

        String evictionPolicy = original != null && original.getEvictionPolicy() != null
            ? original.getEvictionPolicy()
            : "LRU";

        boolean metricsEnabled = original == null || Boolean.TRUE.equals(original.getEnableMetrics());

        return CacheConfig.builder()
            .enabled(enabled)
            .type("memory")
            .defaultTtl(defaultTtl)
            .maxSize(maxSize)
            .evictionPolicy(evictionPolicy)
            .enableMetrics(metricsEnabled)
            .enableStatistics(metricsEnabled)
            .build();
    }
}