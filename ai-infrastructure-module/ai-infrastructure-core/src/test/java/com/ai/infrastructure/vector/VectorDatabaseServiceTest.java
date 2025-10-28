package com.ai.infrastructure.vector;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for VectorDatabaseService
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@DisplayName("VectorDatabaseService Tests")
@ExtendWith(MockitoExtension.class)
class VectorDatabaseServiceTest {
    
    @Mock
    private VectorDatabase mockVectorDatabase;
    
    private VectorDatabaseService vectorDatabaseService;
    
    @BeforeEach
    void setUp() {
        vectorDatabaseService = new VectorDatabaseService(mockVectorDatabase);
    }
    
    @Test
    @DisplayName("Should store entity vector successfully")
    void shouldStoreEntityVectorSuccessfully() {
        // Given
        String entityType = "product";
        String entityId = "prod-123";
        String content = "Luxury handbag";
        List<Double> vector = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("category", "handbags", "brand", "Hermès");
        
        // When
        vectorDatabaseService.storeEntityVector(entityType, entityId, content, vector, metadata);
        
        // Then
        verify(mockVectorDatabase).store(
            eq("product:prod-123"),
            eq(vector),
            argThat(enrichedMetadata -> {
                return enrichedMetadata.get("entityType").equals(entityType) &&
                       enrichedMetadata.get("entityId").equals(entityId) &&
                       enrichedMetadata.get("content").equals(content) &&
                       enrichedMetadata.get("category").equals("handbags") &&
                       enrichedMetadata.get("brand").equals("Hermès");
            })
        );
    }
    
    @Test
    @DisplayName("Should perform batch storage of entity vectors")
    void shouldPerformBatchStorageOfEntityVectors() {
        // Given
        List<EntityVector> entityVectors = Arrays.asList(
            EntityVector.builder()
                .entityType("product")
                .entityId("prod-1")
                .content("Product 1")
                .vector(Arrays.asList(0.1, 0.2, 0.3))
                .metadata(Map.of("category", "A"))
                .build(),
            EntityVector.builder()
                .entityType("product")
                .entityId("prod-2")
                .content("Product 2")
                .vector(Arrays.asList(0.4, 0.5, 0.6))
                .metadata(Map.of("category", "B"))
                .build()
        );
        
        // When
        vectorDatabaseService.batchStoreEntityVectors(entityVectors);
        
        // Then
        verify(mockVectorDatabase).batchStore(argThat(records -> {
            return records.size() == 2 &&
                   records.get(0).getId().equals("product:prod-1") &&
                   records.get(1).getId().equals("product:prod-2");
        }));
    }
    
    @Test
    @DisplayName("Should search similar entities successfully")
    void shouldSearchSimilarEntitiesSuccessfully() {
        // Given
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        String entityType = "product";
        int limit = 5;
        double threshold = 0.7;
        
        List<VectorSearchResult> mockResults = Arrays.asList(
            VectorSearchResult.builder()
                .record(VectorRecord.builder()
                    .id("product:prod-1")
                    .vector(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5))
                    .metadata(Map.of("entityType", "product", "entityId", "prod-1", "content", "Product 1"))
                    .build())
                .similarity(0.95)
                .distance(0.05)
                .build(),
            VectorSearchResult.builder()
                .record(VectorRecord.builder()
                    .id("product:prod-2")
                    .vector(Arrays.asList(0.2, 0.3, 0.4, 0.5, 0.6))
                    .metadata(Map.of("entityType", "product", "entityId", "prod-2", "content", "Product 2"))
                    .build())
                .similarity(0.85)
                .distance(0.15)
                .build()
        );
        
        when(mockVectorDatabase.searchWithFilter(eq(queryVector), eq(Map.of("entityType", entityType)), eq(limit), eq(threshold)))
            .thenReturn(mockResults);
        
        // When
        AISearchResponse response = vectorDatabaseService.searchSimilarEntities(queryVector, entityType, limit, threshold);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getTotalResults()).isEqualTo(2);
        assertThat(response.getMaxScore()).isEqualTo(0.95);
        
        Map<String, Object> firstResult = response.getResults().get(0);
        assertThat(firstResult.get("id")).isEqualTo("prod-1");
        assertThat(firstResult.get("entityType")).isEqualTo("product");
        assertThat(firstResult.get("similarity")).isEqualTo(0.95);
    }
    
    @Test
    @DisplayName("Should search with AI search request")
    void shouldSearchWithAISearchRequest() {
        // Given
        AISearchRequest request = AISearchRequest.builder()
            .entityType("product")
            .limit(10)
            .threshold(0.6)
            .build();
        
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
        
        when(mockVectorDatabase.searchWithFilter(any(), any(), anyInt(), anyDouble()))
            .thenReturn(Collections.emptyList());
        
        // When
        AISearchResponse response = vectorDatabaseService.search(request, queryVector);
        
        // Then
        assertThat(response).isNotNull();
        verify(mockVectorDatabase).searchWithFilter(
            eq(queryVector),
            eq(Map.of("entityType", "product")),
            eq(10),
            eq(0.6)
        );
    }
    
    @Test
    @DisplayName("Should get entity vector by ID")
    void shouldGetEntityVectorById() {
        // Given
        String entityType = "product";
        String entityId = "prod-123";
        String vectorId = "product:prod-123";
        
        VectorRecord mockRecord = VectorRecord.builder()
            .id(vectorId)
            .vector(Arrays.asList(0.1, 0.2, 0.3))
            .metadata(Map.of("entityType", entityType, "entityId", entityId))
            .build();
        
        when(mockVectorDatabase.get(vectorId)).thenReturn(Optional.of(mockRecord));
        
        // When
        Optional<VectorRecord> result = vectorDatabaseService.getEntityVector(entityType, entityId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(vectorId);
        verify(mockVectorDatabase).get(vectorId);
    }
    
    @Test
    @DisplayName("Should delete entity vector")
    void shouldDeleteEntityVector() {
        // Given
        String entityType = "product";
        String entityId = "prod-123";
        String vectorId = "product:prod-123";
        
        when(mockVectorDatabase.delete(vectorId)).thenReturn(true);
        
        // When
        boolean deleted = vectorDatabaseService.deleteEntityVector(entityType, entityId);
        
        // Then
        assertThat(deleted).isTrue();
        verify(mockVectorDatabase).delete(vectorId);
    }
    
    @Test
    @DisplayName("Should get statistics from vector database")
    void shouldGetStatisticsFromVectorDatabase() {
        // Given
        Map<String, Object> mockStats = Map.of(
            "vectorCount", 100,
            "searchCount", 50,
            "averageSearchTimeMs", 25.5
        );
        
        when(mockVectorDatabase.getStatistics()).thenReturn(mockStats);
        
        // When
        Map<String, Object> stats = vectorDatabaseService.getStatistics();
        
        // Then
        assertThat(stats).isEqualTo(mockStats);
        verify(mockVectorDatabase).getStatistics();
    }
    
    @Test
    @DisplayName("Should check health status")
    void shouldCheckHealthStatus() {
        // Given
        when(mockVectorDatabase.isHealthy()).thenReturn(true);
        
        // When
        boolean healthy = vectorDatabaseService.isHealthy();
        
        // Then
        assertThat(healthy).isTrue();
        verify(mockVectorDatabase).isHealthy();
    }
    
    @Test
    @DisplayName("Should get database type")
    void shouldGetDatabaseType() {
        // Given
        when(mockVectorDatabase.getType()).thenReturn("in-memory");
        
        // When
        String type = vectorDatabaseService.getDatabaseType();
        
        // Then
        assertThat(type).isEqualTo("in-memory");
        verify(mockVectorDatabase).getType();
    }
    
    @Test
    @DisplayName("Should clear all vectors")
    void shouldClearAllVectors() {
        // When
        vectorDatabaseService.clearAllVectors();
        
        // Then
        verify(mockVectorDatabase).clear();
    }
    
    @Test
    @DisplayName("Should handle empty batch storage gracefully")
    void shouldHandleEmptyBatchStorageGracefully() {
        // When
        vectorDatabaseService.batchStoreEntityVectors(null);
        vectorDatabaseService.batchStoreEntityVectors(Collections.emptyList());
        
        // Then
        verify(mockVectorDatabase, never()).batchStore(any());
    }
    
    @Test
    @DisplayName("Should search without entity type filter")
    void shouldSearchWithoutEntityTypeFilter() {
        // Given
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
        
        when(mockVectorDatabase.searchWithFilter(eq(queryVector), isNull(), eq(10), eq(0.7)))
            .thenReturn(Collections.emptyList());
        
        // When
        AISearchResponse response = vectorDatabaseService.searchSimilarEntities(queryVector, null, 10, 0.7);
        
        // Then
        assertThat(response).isNotNull();
        verify(mockVectorDatabase).searchWithFilter(queryVector, null, 10, 0.7);
    }
    
    @Test
    @DisplayName("Should handle search with empty entity type")
    void shouldHandleSearchWithEmptyEntityType() {
        // Given
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
        
        when(mockVectorDatabase.searchWithFilter(eq(queryVector), isNull(), eq(10), eq(0.7)))
            .thenReturn(Collections.emptyList());
        
        // When
        AISearchResponse response = vectorDatabaseService.searchSimilarEntities(queryVector, "", 10, 0.7);
        
        // Then
        assertThat(response).isNotNull();
        verify(mockVectorDatabase).searchWithFilter(queryVector, null, 10, 0.7);
    }
}