package com.subscription.hub.config;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.provider.AIProviderManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Stub configuration to provide AICoreService when AI providers are not fully configured.
 * This allows the app to start even if ONNX model files are missing.
 */
@Configuration
@ConditionalOnMissingBean(AICoreService.class)
public class AICoreServiceStubConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public AIProviderManager aiProviderManager(AIProviderConfig config) {
        // Create provider manager with empty provider list
        return new AIProviderManager(Collections.emptyList(), config);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AICoreService aiCoreService(AIProviderConfig config,
                                       AIEmbeddingService embeddingService,
                                       AISearchService searchService,
                                       AIProviderManager providerManager) {
        return new AICoreService(config, embeddingService, searchService, providerManager);
    }
}
