package com.ai.infrastructure.config;

import com.ai.infrastructure.aspect.AICapableAspect;
import com.ai.infrastructure.service.AICapabilityService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.processor.AICapableProcessor;
import com.ai.infrastructure.processor.EmbeddingProcessor;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.cleanup.CleanupPolicyProvider;
import com.ai.infrastructure.cleanup.DefaultCleanupPolicyProvider;
import com.ai.infrastructure.cleanup.SearchableEntityCleanupScheduler;
import com.ai.infrastructure.indexing.IndexingCoordinator;
import com.ai.infrastructure.indexing.IndexingStrategyResolver;
import com.ai.infrastructure.indexing.queue.IndexingQueueService;
import com.ai.infrastructure.indexing.worker.AsyncIndexingWorker;
import com.ai.infrastructure.indexing.worker.BatchIndexingWorker;
import com.ai.infrastructure.indexing.worker.IndexingCleanupScheduler;
import com.ai.infrastructure.indexing.worker.IndexingWorkProcessor;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.compliance.policy.ComplianceCheckProvider;
import com.ai.infrastructure.privacy.AIDataPrivacyService;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.filter.AIContentFilterService;
import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.access.policy.EntityAccessPolicy;
import com.ai.infrastructure.deletion.UserDataDeletionService;
import com.ai.infrastructure.deletion.policy.UserDataDeletionProvider;
import com.ai.infrastructure.deletion.port.BehaviorDeletionPort;
import com.ai.infrastructure.search.VectorSearchService;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.vector.VectorDatabase;
import com.ai.infrastructure.vector.VectorDatabaseServiceAdapter;
import com.ai.infrastructure.health.AIHealthIndicator;
import com.ai.infrastructure.repository.IndexingQueueRepository;
import com.ai.infrastructure.storage.AIStorageProperties;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
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
        SecurityProperties.class,
        AIIndexingProperties.class,
        AICleanupProperties.class,
        AIStorageProperties.class
    })
@Import({ProviderConfiguration.class, AISearchableStorageStrategyAutoConfiguration.class})
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
                                               Clock clock,
                                               SecurityProperties securityProperties) {
        return new AISecurityService(piiDetectionService, clock, securityProperties);
    }
    
    @Bean
    public AIComplianceService aiComplianceService(Clock clock,
                                                   ObjectProvider<ComplianceCheckProvider> complianceProvider) {
        return new AIComplianceService(clock, complianceProvider.getIfAvailable());
    }
    
    @Bean
    public UserDataDeletionService userDataDeletionService(AISearchableEntityStorageStrategy storageStrategy,
                                                           VectorDatabaseService vectorDatabaseService,
                                                           Clock clock,
                                                           ObjectProvider<UserDataDeletionProvider> deletionProvider,
                                                           ObjectProvider<BehaviorDeletionPort> behaviorDeletionPort) {
        return new UserDataDeletionService(
            storageStrategy,
            vectorDatabaseService,
            clock,
            deletionProvider.getIfAvailable(),
            behaviorDeletionPort
        );
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
    public AIAccessControlService aiAccessControlService(Clock clock,
                                                         ObjectProvider<EntityAccessPolicy> entityAccessPolicyProvider) {
        return new AIAccessControlService(
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
    public CleanupPolicyProvider cleanupPolicyProvider(AICleanupProperties cleanupProperties) {
        return new DefaultCleanupPolicyProvider(cleanupProperties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SearchableEntityCleanupScheduler searchableEntityCleanupScheduler(
        AICleanupProperties cleanupProperties,
        CleanupPolicyProvider cleanupPolicyProvider,
        AISearchableEntityStorageStrategy storageStrategy,
        VectorManagementService vectorManagementService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        return new SearchableEntityCleanupScheduler(
            cleanupProperties,
            cleanupPolicyProvider,
            storageStrategy,
            vectorManagementService,
            objectMapper,
            clock
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexingStrategyResolver indexingStrategyResolver() {
        return new IndexingStrategyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexingQueueService indexingQueueService(
        IndexingQueueRepository repository,
        AIIndexingProperties indexingProperties,
        Clock clock
    ) {
        return new IndexingQueueService(repository, indexingProperties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexingWorkProcessor indexingWorkProcessor(
        ObjectMapper objectMapper,
        AIEntityConfigurationLoader configurationLoader,
        AICapabilityService capabilityService
    ) {
        return new IndexingWorkProcessor(objectMapper, configurationLoader, capabilityService);
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexingCoordinator indexingCoordinator(
        IndexingStrategyResolver indexingStrategyResolver,
        IndexingQueueService indexingQueueService,
        AIEntityConfigurationLoader configurationLoader,
        AIIndexingProperties indexingProperties,
        ObjectMapper objectMapper,
        AICapabilityService capabilityService
    ) {
        return new IndexingCoordinator(
            indexingStrategyResolver,
            indexingQueueService,
            configurationLoader,
            indexingProperties,
            objectMapper,
            capabilityService
        );
    }

    @Bean
    public AsyncIndexingWorker asyncIndexingWorker(
        IndexingQueueService indexingQueueService,
        IndexingWorkProcessor indexingWorkProcessor,
        AIIndexingProperties indexingProperties
    ) {
        return new AsyncIndexingWorker(indexingQueueService, indexingWorkProcessor, indexingProperties);
    }

    @Bean
    public BatchIndexingWorker batchIndexingWorker(
        IndexingQueueService indexingQueueService,
        IndexingWorkProcessor indexingWorkProcessor,
        AIIndexingProperties indexingProperties
    ) {
        return new BatchIndexingWorker(indexingQueueService, indexingWorkProcessor, indexingProperties);
    }

    @Bean
    public IndexingCleanupScheduler indexingCleanupScheduler(
        IndexingQueueService indexingQueueService,
        AIIndexingProperties indexingProperties,
        Clock clock
    ) {
        return new IndexingCleanupScheduler(indexingQueueService, indexingProperties, clock);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapabilityService aiCapabilityService(
            AIEmbeddingService embeddingService,
            AICoreService aiCoreService,
            AISearchableEntityStorageStrategy storageStrategy,
            AIEntityConfigurationLoader entityConfigurationLoader,
            VectorManagementService vectorManagementService) {
        return new AICapabilityService(embeddingService, aiCoreService, storageStrategy, entityConfigurationLoader, vectorManagementService);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICapableAspect aiCapableAspect(
            AIEntityConfigurationLoader configLoader,
            AICapabilityService aiCapabilityService,
            IndexingCoordinator indexingCoordinator) {
        return new AICapableAspect(configLoader, aiCapabilityService, indexingCoordinator);
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
    @ConditionalOnMissingBean
    public com.ai.infrastructure.service.AIInfrastructureProfileService aiInfrastructureProfileService(com.ai.infrastructure.repository.AIInfrastructureProfileRepository aiInfrastructureProfileRepository, AICapabilityService aiCapabilityService) {
        return new com.ai.infrastructure.service.AIInfrastructureProfileService(aiInfrastructureProfileRepository, aiCapabilityService);
    }
}