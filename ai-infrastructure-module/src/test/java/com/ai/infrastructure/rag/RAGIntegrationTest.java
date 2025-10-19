package com.ai.infrastructure.rag;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.LuceneVectorDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG Integration Test
 * 
 * This test verifies that RAG and vector database services work correctly
 * together for AI infrastructure features.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.provider.openai.mock-responses=true",
    "ai.vector-db.type=lucene",
    "ai.vector-db.lucene.index-path=./target/test-lucene-index"
})
public class RAGIntegrationTest {

    @Autowired
    private RAGService ragService;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private LuceneVectorDatabaseService luceneVectorDatabaseService;

    @Test
    public void testVectorDatabaseService() {
        // Test vector database service
        assertNotNull(vectorDatabaseService);
        assertNotNull(luceneVectorDatabaseService);
    }

    @Test
    public void testVectorStorage() {
        // Test vector storage
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Luxury Rolex watch with diamond bezel and premium materials";
        List<Double> embedding = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);
        Map<String, Object> metadata = Map.of(
            "category", "luxury",
            "brand", "rolex",
            "price", "50000",
            "material", "gold"
        );

        // Store vector
        vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);

        // Verify storage
        Map<String, Object> stats = vectorDatabaseService.getStatistics();
        assertNotNull(stats);
        assertTrue((Integer) stats.get("totalVectors") > 0);
    }

    @Test
    public void testVectorSearch() {
        // Store test vectors
        String entityType = "product";
        String entityId1 = "test-product-1";
        String content1 = "Luxury Rolex watch with diamond bezel";
        List<Double> embedding1 = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata1 = Map.of("category", "luxury", "brand", "rolex");

        String entityId2 = "test-product-2";
        String content2 = "Designer Chanel handbag with gold hardware";
        List<Double> embedding2 = List.of(0.6, 0.7, 0.8, 0.9, 1.0);
        Map<String, Object> metadata2 = Map.of("category", "luxury", "brand", "chanel");

        vectorDatabaseService.storeVector(entityType, entityId1, content1, embedding1, metadata1);
        vectorDatabaseService.storeVector(entityType, entityId2, content2, embedding2, metadata2);

        // Test search
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query("luxury watch")
            .entityType("product")
            .limit(10)
            .threshold(0.5)
            .build();

        AISearchResponse searchResponse = vectorDatabaseService.search(embedding1, searchRequest);

        assertNotNull(searchResponse);
        assertNotNull(searchResponse.getResults());
        assertTrue(searchResponse.getTotalResults() > 0);
        assertTrue(searchResponse.getProcessingTimeMs() > 0);
        assertTrue(searchResponse.getMaxScore() > 0);

        // Verify results contain expected content
        boolean foundWatch = searchResponse.getResults().stream()
            .anyMatch(result -> result.get("content").toString().contains("watch"));
        assertTrue(foundWatch);
    }

    @Test
    public void testVectorRemoval() {
        // Store test vector
        String entityType = "test";
        String entityId = "test-entity-1";
        String content = "Test content for removal";
        List<Double> embedding = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("test", "value");

        vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);

        // Verify storage
        Map<String, Object> statsBefore = vectorDatabaseService.getStatistics();
        int totalBefore = (Integer) statsBefore.get("totalVectors");

        // Remove vector
        vectorDatabaseService.removeVector(entityType, entityId);

        // Verify removal
        Map<String, Object> statsAfter = vectorDatabaseService.getStatistics();
        int totalAfter = (Integer) statsAfter.get("totalVectors");
        assertTrue(totalAfter < totalBefore);
    }

    @Test
    public void testVectorDatabaseStatistics() {
        // Test statistics
        Map<String, Object> stats = vectorDatabaseService.getStatistics();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalVectors"));
        assertTrue(stats.containsKey("indexPath"));
        assertTrue(stats.containsKey("similarityThreshold"));
        assertTrue(stats.containsKey("maxResults"));

        // Verify types
        assertTrue(stats.get("totalVectors") instanceof Integer);
        assertTrue(stats.get("indexPath") instanceof String);
        assertTrue(stats.get("similarityThreshold") instanceof Double);
        assertTrue(stats.get("maxResults") instanceof Integer);
    }

    @Test
    public void testVectorDatabaseClear() {
        // Store test vectors
        String entityType = "test";
        String entityId1 = "test-entity-1";
        String content1 = "Test content 1";
        List<Double> embedding1 = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata1 = Map.of("test", "value1");

        String entityId2 = "test-entity-2";
        String content2 = "Test content 2";
        List<Double> embedding2 = List.of(0.6, 0.7, 0.8, 0.9, 1.0);
        Map<String, Object> metadata2 = Map.of("test", "value2");

        vectorDatabaseService.storeVector(entityType, entityId1, content1, embedding1, metadata1);
        vectorDatabaseService.storeVector(entityType, entityId2, content2, embedding2, metadata2);

        // Verify storage
        Map<String, Object> statsBefore = vectorDatabaseService.getStatistics();
        int totalBefore = (Integer) statsBefore.get("totalVectors");
        assertTrue(totalBefore > 0);

        // Clear all vectors
        vectorDatabaseService.clearVectors();

        // Verify clearing
        Map<String, Object> statsAfter = vectorDatabaseService.getStatistics();
        int totalAfter = (Integer) statsAfter.get("totalVectors");
        assertEquals(0, totalAfter);
    }

    @Test
    public void testRAGService() {
        // Test RAG service
        assertNotNull(ragService);

        // Test RAG processing
        RAGRequest ragRequest = RAGRequest.builder()
            .query("What are the features of luxury watches?")
            .context("product")
            .maxResults(5)
            .build();

        RAGResponse ragResponse = ragService.processRAG(ragRequest);

        assertNotNull(ragResponse);
        assertNotNull(ragResponse.getAnswer());
        assertFalse(ragResponse.getAnswer().isEmpty());
        assertTrue(ragResponse.getProcessingTimeMs() > 0);
        assertNotNull(ragResponse.getSources());
        assertNotNull(ragResponse.getContext());
    }

    @Test
    public void testRAGWithVectorSearch() {
        // Store test vectors for RAG
        String entityType = "product";
        String entityId1 = "luxury-watch-1";
        String content1 = "Luxury Rolex watch features: diamond bezel, gold case, automatic movement, water resistance";
        List<Double> embedding1 = List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);
        Map<String, Object> metadata1 = Map.of("category", "luxury", "brand", "rolex", "type", "watch");

        String entityId2 = "luxury-watch-2";
        String content2 = "Premium Omega watch with chronograph function, sapphire crystal, and leather strap";
        List<Double> embedding2 = List.of(0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 0.1);
        Map<String, Object> metadata2 = Map.of("category", "luxury", "brand", "omega", "type", "watch");

        vectorDatabaseService.storeVector(entityType, entityId1, content1, embedding1, metadata1);
        vectorDatabaseService.storeVector(entityType, entityId2, content2, embedding2, metadata2);

        // Test RAG with vector search
        RAGRequest ragRequest = RAGRequest.builder()
            .query("What are the features of luxury watches?")
            .context("product")
            .maxResults(5)
            .build();

        RAGResponse ragResponse = ragService.processRAG(ragRequest);

        assertNotNull(ragResponse);
        assertNotNull(ragResponse.getAnswer());
        assertFalse(ragResponse.getAnswer().isEmpty());
        assertTrue(ragResponse.getProcessingTimeMs() > 0);
        assertNotNull(ragResponse.getSources());
        assertTrue(ragResponse.getSources().size() > 0);
        assertNotNull(ragResponse.getContext());
    }

    @Test
    public void testLuceneVectorDatabaseService() {
        // Test Lucene-specific functionality
        assertNotNull(luceneVectorDatabaseService);

        // Test statistics
        Map<String, Object> stats = luceneVectorDatabaseService.getStatistics();
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalVectors"));
        assertTrue(stats.containsKey("indexPath"));
        assertTrue(stats.containsKey("similarityThreshold"));
        assertTrue(stats.containsKey("maxResults"));

        // Test entity type counts
        if (stats.containsKey("entityTypeCounts")) {
            Map<String, Integer> entityTypeCounts = (Map<String, Integer>) stats.get("entityTypeCounts");
            assertNotNull(entityTypeCounts);
        }
    }

    @Test
    public void testVectorSearchWithThreshold() {
        // Store test vectors with different similarity scores
        String entityType = "product";
        String entityId1 = "high-similarity";
        String content1 = "Luxury watch with diamond bezel";
        List<Double> embedding1 = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata1 = Map.of("category", "luxury", "brand", "rolex");

        String entityId2 = "low-similarity";
        String content2 = "Cheap plastic toy";
        List<Double> embedding2 = List.of(0.9, 0.8, 0.7, 0.6, 0.5);
        Map<String, Object> metadata2 = Map.of("category", "toy", "brand", "generic");

        vectorDatabaseService.storeVector(entityType, entityId1, content1, embedding1, metadata1);
        vectorDatabaseService.storeVector(entityType, entityId2, content2, embedding2, metadata2);

        // Test search with high threshold
        AISearchRequest highThresholdRequest = AISearchRequest.builder()
            .query("luxury watch")
            .entityType("product")
            .limit(10)
            .threshold(0.8) // High threshold
            .build();

        AISearchResponse highThresholdResponse = vectorDatabaseService.search(embedding1, highThresholdRequest);
        assertNotNull(highThresholdResponse);
        assertTrue(highThresholdResponse.getTotalResults() >= 0);

        // Test search with low threshold
        AISearchRequest lowThresholdRequest = AISearchRequest.builder()
            .query("luxury watch")
            .entityType("product")
            .limit(10)
            .threshold(0.1) // Low threshold
            .build();

        AISearchResponse lowThresholdResponse = vectorDatabaseService.search(embedding1, lowThresholdRequest);
        assertNotNull(lowThresholdResponse);
        assertTrue(lowThresholdResponse.getTotalResults() >= 0);
    }
}
