package com.ai.infrastructure.core;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.provider.AIProviderManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AICoreServiceTest {
    
    @Mock
    private AIProviderConfig config;
    
    @Mock
    private AIEmbeddingService embeddingService;
    
    @Mock
    private AISearchService searchService;
    
    @Mock
    private AIProviderManager providerManager;
    
    private AICoreService aiCoreService;
    
    @BeforeEach
    void setUp() {
        AIProviderConfig.GenerationDefaults generationDefaults = new AIProviderConfig.GenerationDefaults(
            "openai", "gpt-4o-mini", 2000, 0.3, 60, 100
        );
        AIProviderConfig.EmbeddingDefaults embeddingDefaults = new AIProviderConfig.EmbeddingDefaults(
            "onnx", "text-embedding-3-small"
        );
        when(config.resolveLlmDefaults()).thenReturn(generationDefaults);
        when(config.resolveEmbeddingDefaults()).thenReturn(embeddingDefaults);
        aiCoreService = new AICoreService(config, embeddingService, searchService, providerManager);
    }
    
    @Test
    void testGenerateContent() {
        // Given
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt")
            .systemPrompt("Test system prompt")
            .build();
        
        // When
        when(providerManager.generateContent(any(AIGenerationRequest.class)))
            .thenThrow(new AIServiceException("Provider failure"));
        
        // Then
        assertThrows(Exception.class, () -> aiCoreService.generateContent(request));
    }
    
    @Test
    void testGenerateEmbedding() {
        // Given
        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text("Test text")
            .build();
        
        AIEmbeddingResponse expectedResponse = AIEmbeddingResponse.builder()
            .embedding(java.util.List.of(0.1, 0.2, 0.3))
            .model("text-embedding-3-small")
            .dimensions(3)
            .build();
        
        // When
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(expectedResponse);
        
        AIEmbeddingResponse response = aiCoreService.generateEmbedding(request);
        
        // Then
        assertNotNull(response);
        assertEquals(expectedResponse.getEmbedding(), response.getEmbedding());
        assertEquals(expectedResponse.getModel(), response.getModel());
        assertEquals(expectedResponse.getDimensions(), response.getDimensions());
    }
    
    @Test
    void testPerformSearch() {
        // Given
        AISearchRequest request = AISearchRequest.builder()
            .query("Test query")
            .entityType("test")
            .build();
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(java.util.List.of(0.1, 0.2, 0.3))
            .build();
        
        AISearchResponse expectedResponse = AISearchResponse.builder()
            .results(java.util.List.of())
            .totalResults(0)
            .maxScore(0.0)
            .build();
        
        // When
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        when(searchService.search(any(java.util.List.class), any(AISearchRequest.class)))
            .thenReturn(expectedResponse);
        
        AISearchResponse response = aiCoreService.performSearch(request);
        
        // Then
        assertNotNull(response);
        assertEquals(expectedResponse.getTotalResults(), response.getTotalResults());
        assertEquals(expectedResponse.getMaxScore(), response.getMaxScore());
    }
    
    @Test
    void testGenerateRecommendations() {
        // Given
        String entityType = "test";
        String context = "Test context";
        int limit = 5;
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(java.util.List.of(0.1, 0.2, 0.3))
            .build();
        
        AISearchResponse searchResponse = AISearchResponse.builder()
            .results(java.util.List.of())
            .build();
        
        // When
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        when(searchService.search(any(java.util.List.class), any(AISearchRequest.class)))
            .thenReturn(searchResponse);
        
        java.util.List<java.util.Map<String, Object>> recommendations = 
            aiCoreService.generateRecommendations(entityType, context, limit);
        
        // Then
        assertNotNull(recommendations);
    }
    
    @Test
    void testValidateContent() {
        // Given
        String content = "Test content";
        java.util.Map<String, Object> validationRules = java.util.Map.of(
            "minLength", 5,
            "maxLength", 100
        );
        
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt")
            .systemPrompt("Test system prompt")
            .build();
        
        AIGenerationResponse response = AIGenerationResponse.builder()
            .content("{\"valid\": true, \"errors\": [], \"suggestions\": []}")
            .build();
        
        // When
        when(providerManager.generateContent(any(AIGenerationRequest.class)))
            .thenThrow(new AIServiceException("Provider failure"));
        
        // Then
        assertThrows(Exception.class, () -> aiCoreService.validateContent(content, validationRules));
    }
}
