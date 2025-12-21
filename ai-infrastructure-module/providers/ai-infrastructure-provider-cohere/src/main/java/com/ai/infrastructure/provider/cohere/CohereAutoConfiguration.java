package com.ai.infrastructure.provider.cohere;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.provider.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the Cohere provider module.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(CohereProvider.class)
public class CohereAutoConfiguration {

    @Bean(name = "cohereProviderConfig")
    @ConditionalOnMissingBean(name = "cohereProviderConfig")
    @ConditionalOnProperty(prefix = "ai.providers.cohere", name = "enabled", havingValue = "true")
    public ProviderConfig cohereProviderConfig(AIProviderConfig aiProviderConfig) {
        AIProviderConfig.CohereConfig cohere = aiProviderConfig.getCohere();
        boolean hasApiKey = cohere.getApiKey() != null && !cohere.getApiKey().isBlank();

        return ProviderConfig.builder()
            .providerName("cohere")
            .apiKey(cohere.getApiKey())
            .baseUrl(cohere.getBaseUrl())
            .defaultModel(cohere.getModel())
            .defaultEmbeddingModel(cohere.getEmbeddingModel())
            .maxTokens(cohere.getMaxTokens())
            .temperature(cohere.getTemperature())
            .timeoutSeconds(cohere.getTimeout())
            .maxRetries(3)
            .retryDelayMs(1000L)
            .rateLimitPerMinute(60)
            .rateLimitPerDay(10_000)
            .enabled(cohere.isEnabled() && hasApiKey)
            .priority(cohere.getPriority())
            .build();
    }

    @Bean
    @ConditionalOnBean(name = "cohereProviderConfig")
    public CohereProvider cohereProvider(@Qualifier("cohereProviderConfig") ProviderConfig providerConfig,
                                         ObjectProvider<RestTemplate> restTemplateProvider) {
        RestTemplate restTemplate = restTemplateProvider.getIfAvailable(RestTemplate::new);
        return new CohereProvider(providerConfig, restTemplate);
    }
}
