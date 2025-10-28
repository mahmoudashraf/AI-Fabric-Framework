package com.ai.infrastructure.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for InMemoryVectorDatabase
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@DisplayName("InMemoryVectorDatabase Tests")
class InMemoryVectorDatabaseTest {
    
    private InMemoryVectorDatabase vectorDatabase;
    
    @BeforeEach
    void setUp() {
        vectorDatabase = new InMemoryVectorDatabase();
    }
    
    @Test
    @DisplayName("Should store and retrieve vector successfully")
    void shouldStoreAndRetrieveVector() {
        // Given
        String id = "test-vector-1";
        List<Double> vector = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("category", "test", "type", "unit-test");
        
        // When
        vectorDatabase.store(id, vector, metadata);
        Optional<VectorRecord> retrieved = vectorDatabase.get(id);
        
        // Then
        assertThat(retrieved).isPresent();
        VectorRecord record = retrieved.get();
        assertThat(record.getId()).isEqualTo(id);
        assertThat(record.getVector()).isEqualTo(vector);
        assertThat(record.getMetadata()).containsAllEntriesOf(metadata);
    }
    
    @Test
    @DisplayName("Should perform vector similarity search")
    void shouldPerformVectorSimilaritySearch() {
        // Given - Store test vectors
        vectorDatabase.store("vec1", Arrays.asList(1.0, 0.0, 0.0), Map.of("category", "A"));
        vectorDatabase.store("vec2", Arrays.asList(0.0, 1.0, 0.0), Map.of("category", "B"));
        vectorDatabase.store("vec3", Arrays.asList(0.9, 0.1, 0.0), Map.of("category", "A")); // Similar to vec1
        
        // When - Search for vector similar to vec1
        List<Double> queryVector = Arrays.asList(1.0, 0.0, 0.0);
        List<VectorSearchResult> results = vectorDatabase.search(queryVector, 10, 0.5);
        
        // Then
        assertThat(results).hasSize(2); // vec1 and vec3 should match
        assertThat(results.get(0).getId()).isEqualTo("vec1"); // Exact match should be first
        assertThat(results.get(0).getSimilarity()).isCloseTo(1.0, within(0.001));
        assertThat(results.get(1).getId()).isEqualTo("vec3"); // Similar vector second
        assertThat(results.get(1).getSimilarity()).isGreaterThan(0.5);
    }
    
    @Test
    @DisplayName("Should filter search results by metadata")
    void shouldFilterSearchResultsByMetadata() {
        // Given
        vectorDatabase.store("vec1", Arrays.asList(1.0, 0.0, 0.0), Map.of("category", "A", "type", "test"));
        vectorDatabase.store("vec2", Arrays.asList(0.9, 0.1, 0.0), Map.of("category", "B", "type", "test"));
        vectorDatabase.store("vec3", Arrays.asList(0.8, 0.2, 0.0), Map.of("category", "A", "type", "prod"));
        
        // When - Search with category filter
        List<Double> queryVector = Arrays.asList(1.0, 0.0, 0.0);
        Map<String, Object> filter = Map.of("category", "A");
        List<VectorSearchResult> results = vectorDatabase.searchWithFilter(queryVector, filter, 10, 0.0);
        
        // Then
        assertThat(results).hasSize(2); // Only vec1 and vec3 should match
        assertThat(results).extracting(result -> result.getMetadata().get("category"))
                          .containsOnly("A");
    }
    
    @Test
    @DisplayName("Should handle batch operations")
    void shouldHandleBatchOperations() {
        // Given
        List<VectorRecord> records = Arrays.asList(
            VectorRecord.builder()
                .id("batch1")
                .vector(Arrays.asList(0.1, 0.2, 0.3))
                .metadata(Map.of("batch", "1"))
                .build(),
            VectorRecord.builder()
                .id("batch2")
                .vector(Arrays.asList(0.4, 0.5, 0.6))
                .metadata(Map.of("batch", "1"))
                .build()
        );
        
        // When
        vectorDatabase.batchStore(records);
        
        // Then
        assertThat(vectorDatabase.get("batch1")).isPresent();
        assertThat(vectorDatabase.get("batch2")).isPresent();
        
        Map<String, Object> stats = vectorDatabase.getStatistics();
        assertThat(stats.get("vectorCount")).isEqualTo(2);
    }
    
    @Test
    @DisplayName("Should delete vectors")
    void shouldDeleteVectors() {
        // Given
        vectorDatabase.store("delete-me", Arrays.asList(0.1, 0.2, 0.3), Map.of("temp", "true"));
        vectorDatabase.store("keep-me", Arrays.asList(0.4, 0.5, 0.6), Map.of("temp", "false"));
        
        // When
        boolean deleted = vectorDatabase.delete("delete-me");
        
        // Then
        assertThat(deleted).isTrue();
        assertThat(vectorDatabase.get("delete-me")).isEmpty();
        assertThat(vectorDatabase.get("keep-me")).isPresent();
    }
    
    @Test
    @DisplayName("Should handle batch deletion")
    void shouldHandleBatchDeletion() {
        // Given
        vectorDatabase.store("del1", Arrays.asList(0.1, 0.2, 0.3), Map.of());
        vectorDatabase.store("del2", Arrays.asList(0.4, 0.5, 0.6), Map.of());
        vectorDatabase.store("keep", Arrays.asList(0.7, 0.8, 0.9), Map.of());
        
        // When
        int deletedCount = vectorDatabase.batchDelete(Arrays.asList("del1", "del2", "nonexistent"));
        
        // Then
        assertThat(deletedCount).isEqualTo(2);
        assertThat(vectorDatabase.get("del1")).isEmpty();
        assertThat(vectorDatabase.get("del2")).isEmpty();
        assertThat(vectorDatabase.get("keep")).isPresent();
    }
    
    @Test
    @DisplayName("Should provide accurate statistics")
    void shouldProvideAccurateStatistics() {
        // Given
        vectorDatabase.store("stat1", Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5), Map.of("test", "stats"));
        vectorDatabase.store("stat2", Arrays.asList(0.6, 0.7, 0.8, 0.9, 1.0), Map.of("test", "stats"));
        
        // Perform some searches to generate metrics
        vectorDatabase.search(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5), 10, 0.0);
        vectorDatabase.search(Arrays.asList(0.6, 0.7, 0.8, 0.9, 1.0), 10, 0.0);
        
        // When
        Map<String, Object> stats = vectorDatabase.getStatistics();
        
        // Then
        assertThat(stats.get("type")).isEqualTo("in-memory");
        assertThat(stats.get("vectorCount")).isEqualTo(2);
        assertThat(stats.get("searchCount")).isEqualTo(2);
        assertThat(stats.get("vectorDimensions")).isEqualTo(5);
        assertThat(stats.get("totalSearchTimeMs")).isNotNull();
        assertThat(stats.get("averageSearchTimeMs")).isNotNull();
        assertThat(stats.get("estimatedMemoryUsageBytes")).isNotNull();
    }
    
    @Test
    @DisplayName("Should be healthy")
    void shouldBeHealthy() {
        // When
        boolean healthy = vectorDatabase.isHealthy();
        
        // Then
        assertThat(healthy).isTrue();
    }
    
    @Test
    @DisplayName("Should clear all vectors")
    void shouldClearAllVectors() {
        // Given
        vectorDatabase.store("clear1", Arrays.asList(0.1, 0.2, 0.3), Map.of());
        vectorDatabase.store("clear2", Arrays.asList(0.4, 0.5, 0.6), Map.of());
        
        // When
        vectorDatabase.clear();
        
        // Then
        Map<String, Object> stats = vectorDatabase.getStatistics();
        assertThat(stats.get("vectorCount")).isEqualTo(0);
        assertThat(stats.get("searchCount")).isEqualTo(0);
        assertThat(vectorDatabase.get("clear1")).isEmpty();
        assertThat(vectorDatabase.get("clear2")).isEmpty();
    }
    
    @Test
    @DisplayName("Should handle invalid inputs gracefully")
    void shouldHandleInvalidInputsGracefully() {
        // Test null ID
        assertThatThrownBy(() -> vectorDatabase.store(null, Arrays.asList(0.1, 0.2), Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        
        // Test empty ID
        assertThatThrownBy(() -> vectorDatabase.store("", Arrays.asList(0.1, 0.2), Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        
        // Test null vector
        assertThatThrownBy(() -> vectorDatabase.store("test", null, Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        
        // Test empty vector
        assertThatThrownBy(() -> vectorDatabase.store("test", Arrays.asList(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
        
        // Test null query vector
        assertThatThrownBy(() -> vectorDatabase.search(null, 10, 0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    @DisplayName("Should calculate cosine similarity correctly")
    void shouldCalculateCosineSimilarityCorrectly() {
        // Given - Orthogonal vectors (should have 0 similarity)
        vectorDatabase.store("ortho1", Arrays.asList(1.0, 0.0, 0.0), Map.of());
        vectorDatabase.store("ortho2", Arrays.asList(0.0, 1.0, 0.0), Map.of());
        
        // When
        List<VectorSearchResult> results = vectorDatabase.search(Arrays.asList(1.0, 0.0, 0.0), 10, 0.0);
        
        // Then
        VectorSearchResult exactMatch = results.stream()
            .filter(r -> r.getId().equals("ortho1"))
            .findFirst()
            .orElseThrow();
        assertThat(exactMatch.getSimilarity()).isCloseTo(1.0, within(0.001));
        
        VectorSearchResult orthogonalMatch = results.stream()
            .filter(r -> r.getId().equals("ortho2"))
            .findFirst()
            .orElse(null);
        // Orthogonal vectors should have very low similarity (close to 0)
        if (orthogonalMatch != null) {
            assertThat(orthogonalMatch.getSimilarity()).isCloseTo(0.0, within(0.001));
        }
    }
    
    @Test
    @DisplayName("Should respect similarity threshold")
    void shouldRespectSimilarityThreshold() {
        // Given
        vectorDatabase.store("high", Arrays.asList(1.0, 0.0, 0.0), Map.of());
        vectorDatabase.store("medium", Arrays.asList(0.7, 0.7, 0.0), Map.of());
        vectorDatabase.store("low", Arrays.asList(0.0, 0.0, 1.0), Map.of());
        
        // When - Search with high threshold
        List<VectorSearchResult> results = vectorDatabase.search(Arrays.asList(1.0, 0.0, 0.0), 10, 0.8);
        
        // Then - Only high similarity results should be returned
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(result -> result.getSimilarity() >= 0.8);
    }
}