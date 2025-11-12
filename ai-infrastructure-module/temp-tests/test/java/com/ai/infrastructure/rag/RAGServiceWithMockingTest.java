package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.MockAIConfiguration;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.mock.MockAIResponses;
import com.ai.infrastructure.vector.VectorDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for RAGService with Mocked AI Services
 * 
 * This test demonstrates how to use mocked AI services for testing
 * without external API calls.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@Import(MockAIConfiguration.class)
@ActiveProfiles("test")
class RAGServiceWithMockingTest {
    
    @Mock
    private AIProviderConfig config;
    
    @Mock
    private AIEmbeddingService embeddingService;
    
    @Mock
    private VectorDatabaseService vectorDatabaseService;
    
    @Mock
    private VectorDatabase vectorDatabase;
    
    private RAGService ragService;
    
    @BeforeEach
    void setUp() {
        ragService = new RAGService(config, embeddingService, vectorDatabaseService, vectorDatabase);
        
        // Mock config defaults
        when(config.resolveEmbeddingDefaults()).thenReturn(new AIProviderConfig.EmbeddingDefaults("onnx", "text-embedding-3-small"));
    }
    
    @Test
    void testIndexContentWithMockedEmbedding() {
        // Given
        String entityType = "product";
        String entityId = "luxury-watch-1";
        String content = "Luxury Rolex watch with diamond bezel";
        Map<String, Object> metadata = Map.of("category", "watches", "brand", "Rolex");
        
        // Mock embedding service to return mock response
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(MockAIResponses.luxuryWatchEmbedding());
        
        // When
        ragService.indexContent(entityType, entityId, content, metadata);
        
        // Then
        verify(embeddingService).generateEmbedding(any(AIEmbeddingRequest.class));
        verify(vectorDatabaseService).storeVector(eq(entityType), eq(entityId), eq(content), 
            eq(MockAIResponses.LUXURY_WATCH_EMBEDDING), eq(metadata));
    }
    
    @Test
    void testPerformRAGQueryWithMockedServices() {
        // Given
        String query = "luxury watch";
        String entityType = "product";
        int limit = 5;
        
        // Mock embedding service
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(MockAIResponses.queryEmbedding());
        
        // Mock vector database service
        when(vectorDatabaseService.search(any(List.class), any(AISearchRequest.class)))
            .thenReturn(MockAIResponses.luxuryProductsSearch());
        
        // When
        AISearchResponse response = ragService.performRAGQuery(query, entityType, limit);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalResults()).isEqualTo(3);
        assertThat(response.getResults()).hasSize(3);
        assertThat(response.getQuery()).isEqualTo(query);
        assertThat(response.getMaxScore()).isEqualTo(0.95);
        
        // Verify the first result
        Map<String, Object> firstResult = response.getResults().get(0);
        assertThat(firstResult.get("id")).isEqualTo("product-1");
        assertThat(firstResult.get("content")).isEqualTo("Luxury Rolex watch with diamond bezel");
        assertThat(firstResult.get("score")).isEqualTo(0.95);
        
        verify(embeddingService).generateEmbedding(any(AIEmbeddingRequest.class));
        verify(vectorDatabaseService).search(any(List.class), any(AISearchRequest.class));
    }
    
    @Test
    void testBuildContextWithMockedResults() {
        // Given
        AISearchResponse searchResponse = MockAIResponses.luxuryProductsSearch();
        
        // When
        String context = ragService.buildContext(searchResponse);
        
        // Then
        assertThat(context).isNotNull();
        assertThat(context).contains("Relevant Context:");
        assertThat(context).contains("Luxury Rolex watch with diamond bezel");
        assertThat(context).contains("Designer Chanel handbag with gold hardware");
        assertThat(context).contains("Luxury diamond necklace with platinum setting");
        assertThat(context).contains("Score: 0.950");
        assertThat(context).contains("Score: 0.870");
        assertThat(context).contains("Score: 0.820");
    }
    
    @Test
    void testBuildContextWithEmptyResults() {
        // Given
        AISearchResponse searchResponse = MockAIResponses.emptySearchResponse();
        
        // When
        String context = ragService.buildContext(searchResponse);
        
        // Then
        assertThat(context).isEqualTo("No relevant context found.");
    }
    
    @Test
    void testIndexContentWithDifferentContentTypes() {
        // Given
        Map<String, String> testCases = Map.of(
            "luxury-watch", "Luxury Rolex watch with diamond bezel",
            "designer-handbag", "Chanel designer handbag with gold hardware",
            "luxury-jewelry", "Tiffany diamond necklace with platinum setting",
            "generic-product", "Some random product description"
        );
        
        // Mock embedding service to return different responses based on content
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenAnswer(invocation -> {
                AIEmbeddingRequest request = invocation.getArgument(0);
                return MockAIResponses.ByContentType.embedding(request.getText());
            });
        
        // When & Then
        for (Map.Entry<String, String> testCase : testCases.entrySet()) {
            String entityId = testCase.getKey();
            String content = testCase.getValue();
            
            ragService.indexContent("product", entityId, content, null);
            
            // Verify the correct embedding was used
            verify(embeddingService, atLeastOnce()).generateEmbedding(any(AIEmbeddingRequest.class));
        }
    }
    
    @Test
    void testRAGQueryWithNoResults() {
        // Given
        String query = "nonexistent product";
        String entityType = "product";
        int limit = 5;
        
        // Mock services to return empty results
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(MockAIResponses.queryEmbedding());
        when(vectorDatabaseService.search(any(List.class), any(AISearchRequest.class)))
            .thenReturn(MockAIResponses.emptySearchResponse());
        
        // When
        AISearchResponse response = ragService.performRAGQuery(query, entityType, limit);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalResults()).isEqualTo(0);
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getMaxScore()).isEqualTo(0.0);
    }
    
    @Test
    void testRemoveContent() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        
        // When
        ragService.removeContent(entityType, entityId);
        
        // Then
        verify(vectorDatabaseService).removeVector(entityType, entityId);
    }
    
    @Test
    void testGetStatistics() {
        // Given
        Map<String, Object> expectedStats = Map.of(
            "totalVectors", 100,
            "entityTypes", Set.of("product", "user"),
            "entityTypeCounts", Map.of("product", 80, "user", 20)
        );
        
        when(vectorDatabaseService.getStatistics()).thenReturn(expectedStats);
        
        // When
        Map<String, Object> stats = ragService.getStatistics();
        
        // Then
        assertThat(stats).isEqualTo(expectedStats);
        verify(vectorDatabaseService).getStatistics();
    }
    
    @Test
    void testPerformanceWithMockedServices() {
        // Given
        String query = "luxury watch";
        String entityType = "product";
        int limit = 10;
        
        // Mock services
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(MockAIResponses.queryEmbedding());
        when(vectorDatabaseService.search(any(List.class), any(AISearchRequest.class)))
            .thenReturn(MockAIResponses.luxuryProductsSearch());
        
        // When
        long startTime = System.currentTimeMillis();
        AISearchResponse response = ragService.performRAGQuery(query, entityType, limit);
        long endTime = System.currentTimeMillis();
        
        // Then
        assertThat(response).isNotNull();
        assertThat(endTime - startTime).isLessThan(100); // Should be very fast with mocks
        assertThat(response.getTotalResults()).isGreaterThan(0);
    }
}