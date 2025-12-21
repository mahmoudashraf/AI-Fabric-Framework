package com.ai.infrastructure.provider.anthropic;

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
 * Auto-configuration for the Anthropic provider module.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(AnthropicProvider.class)
public class AnthropicAutoConfiguration {

    @Bean(name = "anthropicProviderConfig")
    @ConditionalOnMissingBean(name = "anthropicProviderConfig")
    @ConditionalOnProperty(prefix = "ai.providers.anthropic", name = "enabled", havingValue = "true")
    public ProviderConfig anthropicProviderConfig(AIProviderConfig aiProviderConfig) {
        AIProviderConfig.AnthropicConfig anthropic = aiProviderConfig.getAnthropic();
        boolean hasApiKey = anthropic.getApiKey() != null && !anthropic.getApiKey().isBlank();

        return ProviderConfig.builder()
            .providerName("anthropic")
            .apiKey(anthropic.getApiKey())
            .baseUrl(anthropic.getBaseUrl())
            .defaultModel(anthropic.getModel())
            .defaultEmbeddingModel(null)
            .maxTokens(anthropic.getMaxTokens())
            .temperature(anthropic.getTemperature())
            .timeoutSeconds(anthropic.getTimeout())
            .maxRetries(3)
            .retryDelayMs(1000L)
            .rateLimitPerMinute(60)
            .rateLimitPerDay(10_000)
            .enabled(anthropic.isEnabled() && hasApiKey)
            .priority(anthropic.getPriority())
            .build();
    }

    @Bean
    @ConditionalOnBean(name = "anthropicProviderConfig")
    public AnthropicProvider anthropicProvider(@Qualifier("anthropicProviderConfig") ProviderConfig providerConfig,
                                               ObjectProvider<RestTemplate> restTemplateProvider) {
        RestTemplate restTemplate = restTemplateProvider.getIfAvailable(RestTemplate::new);
        return new AnthropicProvider(providerConfig, restTemplate);
    }
}
