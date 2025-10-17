package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.vector.VectorDatabase;
import com.ai.infrastructure.vector.VectorDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for RAGService with Lucene backend
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class RAGServiceWithLuceneTest {
    
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
    }
    
    @AfterEach
    void tearDown() {
        // Clean up if needed
    }
    
    @Test
    void testIndexContent() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Luxury watch with diamond bezel";
        Map<String, Object> metadata = Map.of("category", "watches", "price", 5000.0);
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5))
            .model("text-embedding-3-small")
            .build();
        
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        
        // When
        ragService.indexContent(entityType, entityId, content, metadata);
        
        // Then
        verify(embeddingService).generateEmbedding(any(AIEmbeddingRequest.class));
        verify(vectorDatabaseService).storeVector(eq(entityType), eq(entityId), eq(content), 
            any(List.class), eq(metadata));
    }
    
    @Test
    void testPerformRAGQuery() {
        // Given
        String query = "luxury watch";
        String entityType = "product";
        int limit = 5;
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5))
            .model("text-embedding-3-small")
            .build();
        
        List<Map<String, Object>> searchResults = Arrays.asList(
            Map.of("id", "product-1", "content", "Luxury Rolex watch", "score", 0.95),
            Map.of("id", "product-2", "content", "Designer watch", "score", 0.87)
        );
        
        AISearchResponse searchResponse = AISearchResponse.builder()
            .results(searchResults)
            .totalResults(2)
            .maxScore(0.95)
            .processingTimeMs(100L)
            .requestId("test-request-id")
            .query(query)
            .model("text-embedding-3-small")
            .build();
        
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        when(vectorDatabaseService.search(any(List.class), any(AISearchRequest.class)))
            .thenReturn(searchResponse);
        
        // When
        AISearchResponse result = ragService.performRAGQuery(query, entityType, limit);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalResults()).isEqualTo(2);
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getQuery()).isEqualTo(query);
        
        verify(embeddingService).generateEmbedding(any(AIEmbeddingRequest.class));
        verify(vectorDatabaseService).search(any(List.class), any(AISearchRequest.class));
    }
    
    @Test
    void testBuildContext() {
        // Given
        List<Map<String, Object>> results = Arrays.asList(
            Map.of("content", "Luxury Rolex watch with diamond bezel", "score", 0.95),
            Map.of("content", "Designer watch with leather strap", "score", 0.87),
            Map.of("content", "Vintage watch collection", "score", 0.82)
        );
        
        AISearchResponse searchResponse = AISearchResponse.builder()
            .results(results)
            .totalResults(3)
            .maxScore(0.95)
            .processingTimeMs(100L)
            .requestId("test-request-id")
            .query("luxury watch")
            .model("text-embedding-3-small")
            .build();
        
        // When
        String context = ragService.buildContext(searchResponse);
        
        // Then
        assertThat(context).isNotNull();
        assertThat(context).contains("Relevant Context:");
        assertThat(context).contains("Luxury Rolex watch with diamond bezel");
        assertThat(context).contains("Designer watch with leather strap");
        assertThat(context).contains("Vintage watch collection");
        assertThat(context).contains("Score: 0.950");
        assertThat(context).contains("Score: 0.870");
        assertThat(context).contains("Score: 0.820");
    }
    
    @Test
    void testBuildContextWithEmptyResults() {
        // Given
        AISearchResponse searchResponse = AISearchResponse.builder()
            .results(new ArrayList<>())
            .totalResults(0)
            .maxScore(0.0)
            .processingTimeMs(50L)
            .requestId("test-request-id")
            .query("nonexistent")
            .model("text-embedding-3-small")
            .build();
        
        // When
        String context = ragService.buildContext(searchResponse);
        
        // Then
        assertThat(context).isEqualTo("No relevant context found.");
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
    void testIndexContentWithNullMetadata() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Test product";
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(Arrays.asList(0.1, 0.2, 0.3))
            .model("text-embedding-3-small")
            .build();
        
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        
        // When
        ragService.indexContent(entityType, entityId, content, null);
        
        // Then
        verify(embeddingService).generateEmbedding(any(AIEmbeddingRequest.class));
        verify(vectorDatabaseService).storeVector(eq(entityType), eq(entityId), eq(content), 
            any(List.class), isNull());
    }
    
    @Test
    void testPerformRAGQueryWithEmptyResults() {
        // Given
        String query = "nonexistent product";
        String entityType = "product";
        int limit = 5;
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5))
            .model("text-embedding-3-small")
            .build();
        
        AISearchResponse searchResponse = AISearchResponse.builder()
            .results(new ArrayList<>())
            .totalResults(0)
            .maxScore(0.0)
            .processingTimeMs(50L)
            .requestId("test-request-id")
            .query(query)
            .model("text-embedding-3-small")
            .build();
        
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        when(vectorDatabaseService.search(any(List.class), any(AISearchRequest.class)))
            .thenReturn(searchResponse);
        
        // When
        AISearchResponse result = ragService.performRAGQuery(query, entityType, limit);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotalResults()).isEqualTo(0);
        assertThat(result.getResults()).isEmpty();
        assertThat(result.getQuery()).isEqualTo(query);
    }
    
    @Test
    void testIndexContentWithEmbeddingServiceException() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Test product";
        Map<String, Object> metadata = Map.of("test", true);
        
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenThrow(new RuntimeException("Embedding service error"));
        
        // When & Then
        assertThatThrownBy(() -> ragService.indexContent(entityType, entityId, content, metadata))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to index content");
    }
    
    @Test
    void testPerformRAGQueryWithVectorDatabaseException() {
        // Given
        String query = "test query";
        String entityType = "product";
        int limit = 5;
        
        AIEmbeddingResponse embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5))
            .model("text-embedding-3-small")
            .build();
        
        when(embeddingService.generateEmbedding(any(AIEmbeddingRequest.class)))
            .thenReturn(embeddingResponse);
        when(vectorDatabaseService.search(any(List.class), any(AISearchRequest.class)))
            .thenThrow(new RuntimeException("Vector database error"));
        
        // When & Then
        assertThatThrownBy(() -> ragService.performRAGQuery(query, entityType, limit))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to perform RAG query");
    }
}