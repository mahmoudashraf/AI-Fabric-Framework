package com.ai.infrastructure.vector.qdrant;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.rag.SearchableEntityVectorDatabaseService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for Qdrant vector database integration.
 */
@AutoConfiguration
@ConditionalOnClass(QdrantVectorDatabaseService.class)
public class QdrantVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "qdrant")
    public QdrantVectorDatabaseService qdrantVectorDatabaseDelegate(AIProviderConfig providerConfig) {
        return new QdrantVectorDatabaseService(providerConfig);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "qdrant")
    @ConditionalOnMissingBean(VectorDatabaseService.class)
    public VectorDatabaseService qdrantVectorDatabaseService(QdrantVectorDatabaseService delegate,
                                                             AISearchableEntityRepository searchableEntityRepository,
                                                             AIEntityConfigurationLoader configurationLoader) {
        return new SearchableEntityVectorDatabaseService(delegate, searchableEntityRepository, configurationLoader);
    }
}
