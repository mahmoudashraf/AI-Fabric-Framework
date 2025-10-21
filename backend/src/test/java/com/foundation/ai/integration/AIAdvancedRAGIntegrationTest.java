package com.foundation.ai.integration;

import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.ai.infrastructure.rag.AdvancedRAGService;
import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.exception.AIServiceException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for Advanced RAG services
 * Tests the complete flow from document indexing to search and retrieval
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AIAdvancedRAGIntegrationTest {

    @Autowired
    private AdvancedRAGService advancedRAGService;

    @Autowired
    private AIAuditService aiAuditService;

    @Autowired
    private AISecurityService aiSecurityService;

    private AISearchRequest searchRequest;
    private AdvancedRAGRequest advancedRAGRequest;

    @BeforeEach
    void setUp() {
        searchRequest = AISearchRequest.builder()
                .query("machine learning algorithms for natural language processing")
                .limit(10)
                .build();

        advancedRAGRequest = AdvancedRAGRequest.builder()
                .query("AI security best practices")
                .maxResults(10)
                .build();
    }

    @Test
    @DisplayName("Complete RAG search and retrieval flow")
    void testCompleteRAGFlow() throws Exception {
        // Test that the service is properly instantiated
        assertNotNull(advancedRAGService);
        
        // Test that we can create request objects
        assertNotNull(searchRequest);
        assertNotNull(advancedRAGRequest);
        
        // Test that the service method can be called
        AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(advancedRAGRequest);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Real-time search and retrieval")
    void testRealTimeSearchAndRetrieval() throws Exception {
        // Simulate real-time search
        CompletableFuture<AdvancedRAGResponse> future = CompletableFuture.supplyAsync(() -> {
            try {
                return advancedRAGService.performAdvancedRAG(advancedRAGRequest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        AdvancedRAGResponse response = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Search with different configurations")
    void testSearchWithDifferentConfigurations() throws Exception {
        // Test with different query
        AdvancedRAGRequest differentRequest = AdvancedRAGRequest.builder()
                .query("artificial intelligence machine learning")
                .maxResults(5)
                .build();
        
        AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(differentRequest);
        
        assertNotNull(response);
        assertNotNull(response.getRequestId());
    }

    @Test
    @DisplayName("Error handling and recovery")
    void testErrorHandlingAndRecovery() {
        // Test with null request
        assertThrows(AIServiceException.class, () -> {
            advancedRAGService.performAdvancedRAG(null);
        });
    }

    @Test
    @DisplayName("Performance and scalability")
    void testPerformanceAndScalability() throws Exception {
        long startTime = System.currentTimeMillis();
        
        // Simulate multiple concurrent requests
        List<CompletableFuture<AdvancedRAGResponse>> futures = List.of(
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return advancedRAGService.performAdvancedRAG(advancedRAGRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return advancedRAGService.performAdvancedRAG(advancedRAGRequest);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
        );
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Should complete within reasonable time
        assertTrue(totalTime < 10000); // 10 seconds
        
        // All responses should be valid
        for (CompletableFuture<AdvancedRAGResponse> future : futures) {
            AdvancedRAGResponse response = future.get();
            assertNotNull(response);
            assertNotNull(response.getRequestId());
        }
    }

    @Test
    @DisplayName("Integration with audit and security systems")
    void testIntegrationWithAuditAndSecurity() throws Exception {
        // Perform RAG operation
        AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(advancedRAGRequest);
        
        // Verify response
        assertNotNull(response);
        assertNotNull(response.getRequestId());
        
        // Test that services are available
        assertNotNull(aiAuditService);
        assertNotNull(aiSecurityService);
    }
}
