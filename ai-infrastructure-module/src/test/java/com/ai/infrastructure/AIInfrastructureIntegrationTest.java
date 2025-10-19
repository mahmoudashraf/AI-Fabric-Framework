package com.ai.infrastructure;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.*;
import com.ai.infrastructure.monitoring.AIHealthService;
import com.ai.infrastructure.monitoring.AIMetricsService;
import com.ai.infrastructure.monitoring.AIAnalyticsService;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.rag.VectorDatabaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI Infrastructure Integration Test
 * 
 * This test verifies that all AI infrastructure components work together
 * correctly in a Spring Boot application context.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(classes = {AIInfrastructureAutoConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.provider.openai.mock-responses=true",
    "ai.provider.anthropic.mock-responses=true",
    "ai.provider.cohere.mock-responses=true",
    "ai.vector-db.type=lucene",
    "ai.service.caching.enabled=false",
    "ai.service.metrics.enabled=false",
    "ai.service.async.enabled=false"
})
public class AIInfrastructureIntegrationTest {

    @Autowired
    private AIProviderConfig config;

    @Autowired
    private AICoreService coreService;

    @Autowired
    private AIEmbeddingService embeddingService;

    @Autowired
    private AISearchService searchService;

    @Autowired
    private RAGService ragService;

    @Autowired
    private VectorDatabaseService vectorDatabaseService;

    @Autowired
    private AIHealthService healthService;

    @Autowired
    private AIMetricsService metricsService;

    @Autowired
    private AIAnalyticsService analyticsService;

    @Autowired
    private AIProviderManager providerManager;

    @Test
    public void testContextLoads() {
        assertNotNull(config);
        assertNotNull(coreService);
        assertNotNull(embeddingService);
        assertNotNull(searchService);
        assertNotNull(ragService);
        assertNotNull(vectorDatabaseService);
        assertNotNull(healthService);
        assertNotNull(metricsService);
        assertNotNull(analyticsService);
        assertNotNull(providerManager);
    }

    @Test
    public void testEmbeddingGeneration() {
        // Test embedding generation
        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text("Test text for embedding")
            .model("text-embedding-3-small")
            .build();

        AIEmbeddingResponse response = embeddingService.generateEmbedding(request);

        assertNotNull(response);
        assertNotNull(response.getEmbedding());
        assertFalse(response.getEmbedding().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);
        assertEquals("text-embedding-3-small", response.getModel());
    }

    @Test
    public void testContentGeneration() {
        // Test content generation
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Generate a product description for a luxury watch")
            .purpose("description")
            .maxTokens(100)
            .temperature(0.7)
            .build();

        AIGenerationResponse response = coreService.generateContent(request);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertFalse(response.getContent().isEmpty());
        assertTrue(response.getProcessingTimeMs() > 0);
    }

    @Test
    public void testVectorStorageAndSearch() {
        // Test vector storage
        String entityType = "product";
        String entityId = "test-product-1";
        String content = "Luxury Rolex watch with diamond bezel";
        List<Double> embedding = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("category", "luxury", "brand", "rolex");

        vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);

        // Test vector search
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query("luxury watch")
            .entityType("product")
            .limit(10)
            .threshold(0.5)
            .build();

        AISearchResponse searchResponse = vectorDatabaseService.search(embedding, searchRequest);

        assertNotNull(searchResponse);
        assertNotNull(searchResponse.getResults());
        assertTrue(searchResponse.getTotalResults() > 0);
        assertTrue(searchResponse.getProcessingTimeMs() > 0);
    }

    @Test
    public void testRAGService() {
        // Test RAG service
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
    }

    @Test
    public void testHealthMonitoring() {
        // Test health service
        AIHealthDto health = healthService.getHealthStatus();

        assertNotNull(health);
        assertNotNull(health.getStatus());
        assertNotNull(health.getLastUpdated());
        assertTrue(health.getProcessingTimeMs() > 0);
    }

    @Test
    public void testMetricsCollection() {
        // Test metrics service
        Map<String, Object> performanceMetrics = metricsService.getPerformanceMetrics();

        assertNotNull(performanceMetrics);
        assertTrue(performanceMetrics.containsKey("totalRequests"));
        assertTrue(performanceMetrics.containsKey("successfulRequests"));
        assertTrue(performanceMetrics.containsKey("averageResponseTime"));
    }

    @Test
    public void testAnalyticsService() {
        // Test analytics service
        Map<String, Object> analytics = analyticsService.getAnalytics();

        assertNotNull(analytics);
        assertTrue(analytics.containsKey("usageStats"));
        assertTrue(analytics.containsKey("performanceTrends"));
        assertTrue(analytics.containsKey("recommendations"));
    }

    @Test
    public void testProviderManager() {
        // Test provider manager
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt")
            .purpose("test")
            .maxTokens(50)
            .temperature(0.5)
            .build();

        AIGenerationResponse response = providerManager.generateContent(request);

        assertNotNull(response);
        assertNotNull(response.getContent());
        assertTrue(response.getProcessingTimeMs() > 0);
    }

    @Test
    public void testVectorDatabaseStatistics() {
        // Test vector database statistics
        Map<String, Object> stats = vectorDatabaseService.getStatistics();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalVectors"));
        assertTrue(stats.containsKey("indexPath"));
    }

    @Test
    public void testVectorDatabaseOperations() {
        // Test vector database operations
        String entityType = "test";
        String entityId = "test-entity-1";
        String content = "Test content";
        List<Double> embedding = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        Map<String, Object> metadata = Map.of("test", "value");

        // Store vector
        vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);

        // Search vector
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query("test content")
            .entityType("test")
            .limit(10)
            .threshold(0.5)
            .build();

        AISearchResponse searchResponse = vectorDatabaseService.search(embedding, searchRequest);
        assertNotNull(searchResponse);
        assertTrue(searchResponse.getTotalResults() > 0);

        // Remove vector
        vectorDatabaseService.removeVector(entityType, entityId);

        // Search again (should return no results)
        AISearchResponse emptyResponse = vectorDatabaseService.search(embedding, searchRequest);
        assertTrue(emptyResponse.getTotalResults() == 0);
    }
}
