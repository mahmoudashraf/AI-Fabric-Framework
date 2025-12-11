package com.ai.infrastructure.vector.memory;

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
 * Auto-configuration for in-memory vector database integration.
 */
@AutoConfiguration
@ConditionalOnClass(InMemoryVectorDatabaseService.class)
public class MemoryVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "memory")
    public InMemoryVectorDatabaseService inMemoryVectorDatabaseDelegate(AIProviderConfig config) {
        return new InMemoryVectorDatabaseService(config);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "memory")
    @ConditionalOnMissingBean(VectorDatabaseService.class)
    public VectorDatabaseService inMemoryVectorDatabaseService(InMemoryVectorDatabaseService delegate,
                                                               AISearchableEntityStorageStrategy storageStrategy,
                                                               AIEntityConfigurationLoader configurationLoader) {
        return new SearchableEntityVectorDatabaseService(delegate, storageStrategy, configurationLoader);
    }
}
