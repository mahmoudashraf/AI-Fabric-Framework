package com.ai.infrastructure.vector.pinecone;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.rag.SearchableEntityVectorDatabaseService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for Pinecone-backed vector database integration.
 */
@AutoConfiguration
@ConditionalOnClass(PineconeVectorDatabaseService.class)
public class PineconeVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pinecone")
    public PineconeVectorDatabaseService pineconeVectorDatabaseDelegate(AIProviderConfig config,
                                                                        ObjectProvider<RestTemplate> restTemplateProvider) {
        RestTemplate restTemplate = restTemplateProvider.getIfAvailable(RestTemplate::new);
        return new PineconeVectorDatabaseService(config, restTemplate);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "ai.vector-db.type", havingValue = "pinecone")
    @ConditionalOnMissingBean(VectorDatabaseService.class)
    public VectorDatabaseService pineconeVectorDatabaseService(PineconeVectorDatabaseService delegate,
                                                               AISearchableEntityStorageStrategy storageStrategy,
                                                               AIEntityConfigurationLoader configurationLoader) {
        return new SearchableEntityVectorDatabaseService(delegate, storageStrategy, configurationLoader);
    }
}
