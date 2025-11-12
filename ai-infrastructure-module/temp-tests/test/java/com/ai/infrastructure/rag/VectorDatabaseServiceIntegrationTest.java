package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for Vector Database Services
 * 
 * This test verifies that the vector database services work correctly
 * with different backend implementations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
class VectorDatabaseServiceIntegrationTest {
    
    @Autowired
    private VectorDatabaseService vectorDatabaseService;
    
    @Autowired
    private AIProviderConfig config;
    
    @Test
    void testVectorDatabaseServiceIsAvailable() {
        assertThat(vectorDatabaseService).isNotNull();
    }
    
    @Test
    void testStoreAndRetrieveVector() {
        // Given
        String entityType = "product";
        String entityId = "integration-test-1";
        String content = "Integration test product";
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("test", true, "type", "integration");
        
        // When
        vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);
        
        // Then
        Map<String, Object> stats = vectorDatabaseService.getStatistics();
        assertThat(stats.get("totalVectors")).isEqualTo(1);
    }
    
    @Test
    void testSearchFunctionality() {
        // Given
        String entityType = "product";
        String content = "Luxury watch with diamond bezel";
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("category", "watches", "luxury", true);
        
        vectorDatabaseService.storeVector(entityType, "watch-1", content, embedding, metadata);
        
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        AISearchRequest request = AISearchRequest.builder()
            .query("luxury watch")
            .entityType(entityType)
            .limit(10)
            .threshold(0.5)
            .build();
        
        // When
        AISearchResponse response = vectorDatabaseService.search(queryVector, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalResults()).isGreaterThan(0);
        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getQuery()).isEqualTo("luxury watch");
        assertThat(response.getModel()).isEqualTo(config.resolveEmbeddingDefaults().model());
    }
    
    @Test
    void testRemoveVector() {
        // Given
        String entityType = "product";
        String entityId = "to-be-removed";
        String content = "Product to be removed";
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3);
        
        vectorDatabaseService.storeVector(entityType, entityId, content, embedding, null);
        
        // Verify it exists
        Map<String, Object> statsBefore = vectorDatabaseService.getStatistics();
        assertThat(statsBefore.get("totalVectors")).isEqualTo(1);
        
        // When
        vectorDatabaseService.removeVector(entityType, entityId);
        
        // Then
        Map<String, Object> statsAfter = vectorDatabaseService.getStatistics();
        assertThat(statsAfter.get("totalVectors")).isEqualTo(0);
    }
    
    @Test
    void testClearAllVectors() {
        // Given
        vectorDatabaseService.storeVector("product", "1", "Product 1", Arrays.asList(0.1, 0.2), null);
        vectorDatabaseService.storeVector("product", "2", "Product 2", Arrays.asList(0.3, 0.4), null);
        vectorDatabaseService.storeVector("user", "1", "User 1", Arrays.asList(0.5, 0.6), null);
        
        Map<String, Object> statsBefore = vectorDatabaseService.getStatistics();
        assertThat(statsBefore.get("totalVectors")).isEqualTo(3);
        
        // When
        vectorDatabaseService.clearVectors();
        
        // Then
        Map<String, Object> statsAfter = vectorDatabaseService.getStatistics();
        assertThat(statsAfter.get("totalVectors")).isEqualTo(0);
    }
    
    @Test
    void testMultipleEntityTypes() {
        // Given
        vectorDatabaseService.storeVector("product", "p1", "Luxury watch", Arrays.asList(0.1, 0.2), null);
        vectorDatabaseService.storeVector("product", "p2", "Designer handbag", Arrays.asList(0.3, 0.4), null);
        vectorDatabaseService.storeVector("user", "u1", "John Doe", Arrays.asList(0.5, 0.6), null);
        vectorDatabaseService.storeVector("order", "o1", "Order 123", Arrays.asList(0.7, 0.8), null);
        
        // When
        Map<String, Object> stats = vectorDatabaseService.getStatistics();
        
        // Then
        assertThat(stats.get("totalVectors")).isEqualTo(4);
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> entityTypeCounts = (Map<String, Integer>) stats.get("entityTypeCounts");
        assertThat(entityTypeCounts.get("product")).isEqualTo(2);
        assertThat(entityTypeCounts.get("user")).isEqualTo(1);
        assertThat(entityTypeCounts.get("order")).isEqualTo(1);
    }
    
    @Test
    void testSearchWithEmptyResults() {
        // Given
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
        AISearchRequest request = AISearchRequest.builder()
            .query("nonexistent content")
            .entityType("product")
            .limit(10)
            .threshold(0.9) // High threshold
            .build();
        
        // When
        AISearchResponse response = vectorDatabaseService.search(queryVector, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalResults()).isEqualTo(0);
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getMaxScore()).isEqualTo(0.0);
    }
}