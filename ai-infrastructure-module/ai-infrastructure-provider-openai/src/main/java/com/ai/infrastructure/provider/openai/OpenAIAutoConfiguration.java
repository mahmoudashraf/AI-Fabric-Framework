package com.ai.infrastructure.provider.openai;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the OpenAI provider module.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(OpenAIProvider.class)
public class OpenAIAutoConfiguration {

    @Bean(name = "openAIProviderConfig")
    @ConditionalOnMissingBean(name = "openAIProviderConfig")
    @ConditionalOnProperty(prefix = "ai.providers.openai", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProviderConfig openAIProviderConfig(AIProviderConfig aiProviderConfig) {
        AIProviderConfig.OpenAIConfig openai = aiProviderConfig.getOpenai();
        boolean hasApiKey = openai.getApiKey() != null && !openai.getApiKey().isBlank();

        return ProviderConfig.builder()
            .providerName("openai")
            .apiKey(openai.getApiKey())
            .baseUrl(openai.getBaseUrl())
            .defaultModel(openai.getModel())
            .defaultEmbeddingModel(openai.getEmbeddingModel())
            .maxTokens(openai.getMaxTokens())
            .temperature(openai.getTemperature())
            .timeoutSeconds(openai.getTimeout())
            .maxRetries(3)
            .retryDelayMs(1000L)
            .rateLimitPerMinute(60)
            .rateLimitPerDay(10_000)
            .enabled(openai.isEnabled() && hasApiKey)
            .priority(openai.getPriority())
            .build();
    }

    @Bean
    @ConditionalOnBean(name = "openAIProviderConfig")
    public OpenAIProvider openAIProvider(@Qualifier("openAIProviderConfig") ProviderConfig providerConfig,
                                         ObjectProvider<RestTemplate> restTemplateProvider) {
        RestTemplate restTemplate = restTemplateProvider.getIfAvailable(RestTemplate::new);
        return new OpenAIProvider(providerConfig, restTemplate);
    }

    @Bean
    @ConditionalOnBean(name = "openAIProviderConfig")
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "openai")
    public EmbeddingProvider openAIEmbeddingProvider(AIProviderConfig aiProviderConfig) {
        return new OpenAIEmbeddingProvider(aiProviderConfig);
    }
}
