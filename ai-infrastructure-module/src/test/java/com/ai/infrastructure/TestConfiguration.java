package com.ai.infrastructure;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.LuceneVectorDatabaseService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for AI infrastructure tests
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@TestConfiguration
public class TestConfiguration {
    
    @Bean
    @Primary
    public AIProviderConfig aiProviderConfig() {
        AIProviderConfig config = new AIProviderConfig();
        config.setOpenaiApiKey("test-key");
        config.setOpenaiModel("gpt-4o-mini");
        config.setOpenaiEmbeddingModel("text-embedding-3-small");
        return config;
    }
    
    @Bean
    @Primary
    public VectorDatabaseService vectorDatabaseService(AIProviderConfig config) {
        return new LuceneVectorDatabaseService(config);
    }
}