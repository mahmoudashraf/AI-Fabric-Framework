package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.vector.lucene.LuceneVectorDatabaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class for LuceneVectorDatabaseService
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class LuceneVectorDatabaseServiceTest {
    
    @Mock
    private AIProviderConfig config;
    
    private LuceneVectorDatabaseService service;
    
    @BeforeEach
    void setUp() {
        service = new LuceneVectorDatabaseService(config);
        
        // Set test configuration
        ReflectionTestUtils.setField(service, "indexPath", "./data/test-lucene-index");
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.5);
        ReflectionTestUtils.setField(service, "maxResults", 10);
        
        // Mock configuration defaults
        lenient().when(config.resolveEmbeddingDefaults())
            .thenReturn(new AIProviderConfig.EmbeddingDefaults("onnx", "text-embedding-3-small"));
        
        // Initialize the service
        service.initialize();
    }
    
    @AfterEach
    void tearDown() {
        try {
            service.clearVectors();
            service.cleanup();
        } catch (Exception e) {
            // Ignore cleanup errors in tests
        }
    }
    
    @Test
    void testStoreVector() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Test product description";
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("category", "electronics", "price", 100.0);
        
        // When
        service.storeVector(entityType, entityId, content, embedding, metadata);
        
        // Then
        Map<String, Object> stats = service.getStatistics();
        assertThat(stats.get("totalVectors")).isEqualTo(1);
        assertThat(stats.get("entityTypes")).isInstanceOf(Set.class);
        @SuppressWarnings("unchecked")
        Set<String> entityTypes = (Set<String>) stats.get("entityTypes");
        assertThat(entityTypes).contains(entityType);
    }
    
    @Test
    void testSearchVectors() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Luxury watch with gold case";
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("category", "watches", "price", 1000.0);
        
        service.storeVector(entityType, entityId, content, embedding, metadata);
        
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        AISearchRequest request = AISearchRequest.builder()
            .query("luxury watch")
            .entityType(entityType)
            .limit(10)
            .threshold(0.5)
            .build();
        
        // When
        AISearchResponse response = service.search(queryVector, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalResults()).isGreaterThan(0);
        assertThat(response.getResults()).isNotEmpty();
        assertThat(response.getResults().get(0).get("id")).isEqualTo(entityId);
        assertThat(response.getResults().get(0).get("content")).isEqualTo(content);
    }
    
    @Test
    void testSearchWithNoResults() {
        // Given
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        AISearchRequest request = AISearchRequest.builder()
            .query("nonexistent product")
            .entityType("product")
            .limit(10)
            .threshold(0.9) // High threshold
            .build();
        
        // When
        AISearchResponse response = service.search(queryVector, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalResults()).isEqualTo(0);
        assertThat(response.getResults()).isEmpty();
    }
    
    @Test
    void testRemoveVector() {
        // Given
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Test product";
        List<Double> embedding = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        
        service.storeVector(entityType, entityId, content, embedding, null);
        
        // Verify it exists
        Map<String, Object> statsBefore = service.getStatistics();
        assertThat(statsBefore.get("totalVectors")).isEqualTo(1);
        
        // When
        service.removeVector(entityType, entityId);
        
        // Then
        Map<String, Object> statsAfter = service.getStatistics();
        assertThat(statsAfter.get("totalVectors")).isEqualTo(0);
    }
    
    @Test
    void testClearVectors() {
        // Given
        service.storeVector("product", "1", "Product 1", Arrays.asList(0.1, 0.2), null);
        service.storeVector("product", "2", "Product 2", Arrays.asList(0.3, 0.4), null);
        service.storeVector("user", "1", "User 1", Arrays.asList(0.5, 0.6), null);
        
        Map<String, Object> statsBefore = service.getStatistics();
        assertThat(statsBefore.get("totalVectors")).isEqualTo(3);
        
        // When
        service.clearVectors();
        
        // Then
        Map<String, Object> statsAfter = service.getStatistics();
        assertThat(statsAfter.get("totalVectors")).isEqualTo(0);
    }
    
    @Test
    void testGetStatistics() {
        // Given
        service.storeVector("product", "1", "Product 1", Arrays.asList(0.1, 0.2), null);
        service.storeVector("product", "2", "Product 2", Arrays.asList(0.3, 0.4), null);
        service.storeVector("user", "1", "User 1", Arrays.asList(0.5, 0.6), null);
        
        // When
        Map<String, Object> stats = service.getStatistics();
        
        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.get("totalVectors")).isEqualTo(3);
        assertThat(stats.get("indexPath")).isEqualTo("./data/test-lucene-index");
        assertThat(stats.get("similarityThreshold")).isEqualTo(0.5);
        assertThat(stats.get("maxResults")).isEqualTo(10);
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> entityTypeCounts = (Map<String, Integer>) stats.get("entityTypeCounts");
        assertThat(entityTypeCounts.get("product")).isEqualTo(2);
        assertThat(entityTypeCounts.get("user")).isEqualTo(1);
    }
    
    @Test
    void testSearchWithDifferentEntityTypes() {
        // Given
        service.storeVector("product", "1", "Luxury watch", Arrays.asList(0.1, 0.2, 0.3), null);
        service.storeVector("user", "1", "John Doe", Arrays.asList(0.4, 0.5, 0.6), null);
        
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
        AISearchRequest request = AISearchRequest.builder()
            .query("luxury")
            .entityType("product")
            .limit(10)
            .threshold(0.5)
            .build();
        
        // When
        AISearchResponse response = service.search(queryVector, request);
        
        // Then
        assertThat(response.getTotalResults()).isEqualTo(1);
        assertThat(response.getResults().get(0).get("entityType")).isEqualTo("product");
    }
    
    @Test
    void testSearchWithMetadata() {
        // Given
        Map<String, Object> metadata = Map.of(
            "category", "watches",
            "brand", "Rolex",
            "price", 5000.0
        );
        
        service.storeVector("product", "1", "Luxury Rolex watch", 
            Arrays.asList(0.1, 0.2, 0.3), metadata);
        
        List<Double> queryVector = Arrays.asList(0.1, 0.2, 0.3);
        AISearchRequest request = AISearchRequest.builder()
            .query("Rolex watch")
            .entityType("product")
            .limit(10)
            .threshold(0.5)
            .build();
        
        // When
        AISearchResponse response = service.search(queryVector, request);
        
        // Then
        assertThat(response.getTotalResults()).isEqualTo(1);
        Map<String, Object> result = response.getResults().get(0);
        assertThat(result.get("metadata")).isNotNull();
        assertThat(result.get("id")).isEqualTo("1");
    }
    
    @Test
    void testSimilarityCalculation() {
        // Given - Store vectors with known similarity
        List<Double> vector1 = Arrays.asList(1.0, 0.0, 0.0);
        List<Double> vector2 = Arrays.asList(1.0, 0.0, 0.0); // Identical vector
        List<Double> vector3 = Arrays.asList(0.0, 1.0, 0.0); // Orthogonal vector
        
        service.storeVector("test", "1", "Test 1", vector1, null);
        service.storeVector("test", "2", "Test 2", vector2, null);
        service.storeVector("test", "3", "Test 3", vector3, null);
        
        // When - Search with identical vector
        AISearchRequest request = AISearchRequest.builder()
            .query("test")
            .entityType("test")
            .limit(10)
            .threshold(0.5)
            .build();
        
        AISearchResponse response = service.search(vector1, request);
        
        // Then
        assertThat(response.getTotalResults()).isGreaterThanOrEqualTo(2);
        
        // The identical vector should have the highest similarity
        List<Map<String, Object>> results = response.getResults();
        assertThat(results).isNotEmpty();
        
        // Check that similarity scores are reasonable
        for (Map<String, Object> result : results) {
            Double similarity = (Double) result.get("similarity");
            assertThat(similarity).isBetween(0.0, 1.0);
        }
    }
}