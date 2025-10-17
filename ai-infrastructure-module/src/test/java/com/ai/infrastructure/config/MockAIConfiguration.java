package com.ai.infrastructure.config;

import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.mock.MockAIResponses;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Mock AI Configuration for Testing
 * 
 * This configuration provides mock implementations of AI services
 * for testing without external API calls.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@TestConfiguration
@Profile("test")
public class MockAIConfiguration {
    
    @Bean
    @Primary
    public AIEmbeddingService mockAIEmbeddingService() {
        return new AIEmbeddingService() {
            @Override
            public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
                // Return mock embedding based on content
                return MockAIResponses.ByContentType.embedding(request.getText());
            }
        };
    }
    
    @Bean
    @Primary
    public AICoreService mockAICoreService() {
        return new AICoreService() {
            @Override
            public AIGenerationResponse generateContent(AIGenerationRequest request) {
                // Return mock generation based on purpose
                String purpose = request.getPurpose() != null ? request.getPurpose() : "general";
                return MockAIResponses.ByContentType.generation(purpose);
            }
            
            @Override
            public AISearchResponse performSearch(AISearchRequest request) {
                // Return mock search results
                if (request.getQuery().toLowerCase().contains("luxury")) {
                    return MockAIResponses.luxuryProductsSearch();
                } else {
                    return MockAIResponses.emptySearchResponse();
                }
            }
        };
    }
}