package com.ai.infrastructure.provider.gemini;

import com.ai.infrastructure.config.AIInfrastructureAutoConfiguration;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for the Google Gemini provider module.
 */
@Slf4j
@AutoConfiguration
@AutoConfigureAfter(AIInfrastructureAutoConfiguration.class)
@ConditionalOnClass(GeminiProvider.class)
public class GeminiAutoConfiguration {

    @Bean(name = "geminiProviderConfig")
    @ConditionalOnMissingBean(name = "geminiProviderConfig")
    @ConditionalOnProperty(prefix = "ai.providers.gemini", name = "enabled", havingValue = "true")
    public ProviderConfig geminiProviderConfig(AIProviderConfig aiProviderConfig) {
        AIProviderConfig.GeminiConfig gemini = aiProviderConfig.getGemini();
        boolean hasApiKey = gemini.getApiKey() != null && !gemini.getApiKey().isBlank();

        return ProviderConfig.builder()
            .providerName("gemini")
            .apiKey(gemini.getApiKey())
            .baseUrl(gemini.getBaseUrl())
            .defaultModel(gemini.getModel())
            .defaultEmbeddingModel(gemini.getEmbeddingModel())
            .maxTokens(gemini.getMaxTokens())
            .temperature(gemini.getTemperature())
            .timeoutSeconds(gemini.getTimeout())
            .maxRetries(3)
            .retryDelayMs(1000L)
            .rateLimitPerMinute(60)
            .rateLimitPerDay(10_000)
            .enabled(gemini.isEnabled() && hasApiKey)
            .priority(gemini.getPriority())
            .build();
    }

    @Bean
    @ConditionalOnBean(name = "geminiProviderConfig")
    public GeminiProvider geminiProvider(@Qualifier("geminiProviderConfig") ProviderConfig providerConfig,
                                       ObjectProvider<RestTemplate> restTemplateProvider) {
        RestTemplate restTemplate = restTemplateProvider.getIfAvailable(RestTemplate::new);
        return new GeminiProvider(providerConfig, restTemplate);
    }

    @Bean
    @ConditionalOnBean(name = "geminiProviderConfig")
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "gemini")
    public EmbeddingProvider geminiEmbeddingProvider(AIProviderConfig aiProviderConfig) {
        return new GeminiEmbeddingProvider(aiProviderConfig);
    }
}
