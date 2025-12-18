package com.ai.infrastructure.vector.weaviate;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.rag.SearchableEntityVectorDatabaseService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for Weaviate vector database integration.
 */
@AutoConfiguration
@ConditionalOnClass(WeaviateVectorDatabaseService.class)
public class WeaviateVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "weaviate")
    public WeaviateVectorDatabaseService weaviateVectorDatabaseDelegate(AIProviderConfig providerConfig) {
        return new WeaviateVectorDatabaseService(providerConfig);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "weaviate")
    @ConditionalOnMissingBean(VectorDatabaseService.class)
    public VectorDatabaseService weaviateVectorDatabaseService(WeaviateVectorDatabaseService delegate,
                                                               AISearchableEntityStorageStrategy storageStrategy,
                                                               AIEntityConfigurationLoader configurationLoader) {
        return new SearchableEntityVectorDatabaseService(delegate, storageStrategy, configurationLoader);
    }
}
