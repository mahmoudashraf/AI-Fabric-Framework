package com.ai.infrastructure.provider.rest;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the REST embedding provider module.
 */
@AutoConfiguration
@ConditionalOnClass(RestEmbeddingProvider.class)
public class RestEmbeddingAutoConfiguration {

    @Bean(name = "restEmbeddingProvider")
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "rest")
    public EmbeddingProvider restEmbeddingProvider(AIProviderConfig config) {
        return new RestEmbeddingProvider(config);
    }
}
