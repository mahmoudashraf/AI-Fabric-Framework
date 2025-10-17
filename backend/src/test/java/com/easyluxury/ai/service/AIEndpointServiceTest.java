package com.easyluxury.ai.service;

import com.easyluxury.ai.dto.*;
import com.easyluxury.ai.facade.AIFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AI Endpoint Service Test
 * 
 * Unit tests for AI Endpoint Service.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AIEndpointServiceTest {
    
    @Mock
    private AIFacade aiFacade;
    
    @InjectMocks
    private AIEndpointService aiEndpointService;
    
    private AIGenerationRequest generationRequest;
    private AIGenerationResponse generationResponse;
    private AIEmbeddingRequest embeddingRequest;
    private AIEmbeddingResponse embeddingResponse;
    private AISearchRequest searchRequest;
    private AISearchResponse searchResponse;
    private RAGRequest ragRequest;
    private RAGResponse ragResponse;
    
    @BeforeEach
    void setUp() {
        // Setup test data
        generationRequest = AIGenerationRequest.builder()
            .prompt("Test prompt")
            .maxTokens(100)
            .temperature(0.7)
            .userId("user123")
            .build();
        
        generationResponse = AIGenerationResponse.builder()
            .content("Generated content")
            .model("gpt-4o-mini")
            .timestamp(LocalDateTime.now())
            .generationId("gen_123")
            .requestId("user123")
            .status("SUCCESS")
            .processingTimeMs(1000L)
            .build();
        
        embeddingRequest = AIEmbeddingRequest.builder()
            .input("Test input")
            .model("text-embedding-3-small")
            .userId("user123")
            .build();
        
        embeddingResponse = AIEmbeddingResponse.builder()
            .embeddings(List.of(List.of(0.1, 0.2, 0.3)))
            .model("text-embedding-3-small")
            .dimensions(3)
            .timestamp(LocalDateTime.now())
            .embeddingId("emb_123")
            .requestId("user123")
            .status("SUCCESS")
            .processingTimeMs(500L)
            .input("Test input")
            .build();
        
        searchRequest = AISearchRequest.builder()
            .query("Test query")
            .limit(10)
            .userId("user123")
            .build();
        
        searchResponse = AISearchResponse.builder()
            .results(List.of())
            .totalResults(0L)
            .returnedResults(0)
            .query("Test query")
            .timestamp(LocalDateTime.now())
            .searchId("search_123")
            .requestId("user123")
            .status("SUCCESS")
            .processingTimeMs(300L)
            .build();
        
        ragRequest = RAGRequest.builder()
            .question("Test question")
            .knowledgeBase("test")
            .userId("user123")
            .build();
        
        ragResponse = RAGResponse.builder()
            .answer("Test answer")
            .question("Test question")
            .knowledgeBase("test")
            .timestamp(LocalDateTime.now())
            .ragId("rag_123")
            .requestId("user123")
            .status("SUCCESS")
            .processingTimeMs(2000L)
            .confidence(0.9)
            .documentsRetrieved(3)
            .documentsUsed(2)
            .build();
    }
    
    // ==================== AI Generation Tests ====================
    
    @Test
    void testProcessGenerationRequest_Success() {
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(generationResponse);
        
        AIGenerationResponse result = aiEndpointService.processGenerationRequest(generationRequest);
        
        assertNotNull(result);
        assertEquals("Generated content", result.getContent());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("user123", result.getRequestId());
        assertNotNull(result.getTimestamp());
        
        verify(aiFacade).generateContent(any(AIGenerationRequest.class));
    }
    
    @Test
    void testProcessGenerationRequest_ValidationError() {
        AIGenerationRequest invalidRequest = AIGenerationRequest.builder()
            .prompt("") // Empty prompt should fail validation
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processGenerationRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).generateContent(any());
    }
    
    @Test
    void testProcessGenerationRequest_NullRequest() {
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processGenerationRequest(null);
        });
        
        verify(aiFacade, never()).generateContent(any());
    }
    
    @Test
    void testProcessGenerationRequest_TooLongPrompt() {
        AIGenerationRequest invalidRequest = AIGenerationRequest.builder()
            .prompt("x".repeat(4001)) // Too long prompt
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processGenerationRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).generateContent(any());
    }
    
    @Test
    void testProcessQuickGeneration_Success() {
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(generationResponse);
        
        AIGenerationResponse result = aiEndpointService.processQuickGeneration(
            "Test prompt", 100, 0.7, "user123");
        
        assertNotNull(result);
        assertEquals("Generated content", result.getContent());
        assertEquals("SUCCESS", result.getStatus());
        
        verify(aiFacade).generateContent(any(AIGenerationRequest.class));
    }
    
    @Test
    void testProcessQuickGeneration_WithNulls() {
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(generationResponse);
        
        AIGenerationResponse result = aiEndpointService.processQuickGeneration(
            "Test prompt", null, null, "user123");
        
        assertNotNull(result);
        assertEquals("Generated content", result.getContent());
        
        verify(aiFacade).generateContent(any(AIGenerationRequest.class));
    }
    
    // ==================== AI Embedding Tests ====================
    
    @Test
    void testProcessEmbeddingRequest_Success() {
        when(aiFacade.generateEmbeddings(any(AIEmbeddingRequest.class))).thenReturn(embeddingResponse);
        
        AIEmbeddingResponse result = aiEndpointService.processEmbeddingRequest(embeddingRequest);
        
        assertNotNull(result);
        assertNotNull(result.getEmbeddings());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("user123", result.getRequestId());
        assertNotNull(result.getTimestamp());
        
        verify(aiFacade).generateEmbeddings(any(AIEmbeddingRequest.class));
    }
    
    @Test
    void testProcessEmbeddingRequest_ValidationError() {
        AIEmbeddingRequest invalidRequest = AIEmbeddingRequest.builder()
            .input("") // Empty input should fail validation
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processEmbeddingRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).generateEmbeddings(any());
    }
    
    @Test
    void testProcessEmbeddingRequest_TooLongInput() {
        AIEmbeddingRequest invalidRequest = AIEmbeddingRequest.builder()
            .input("x".repeat(8001)) // Too long input
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processEmbeddingRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).generateEmbeddings(any());
    }
    
    // ==================== AI Search Tests ====================
    
    @Test
    void testProcessSearchRequest_Success() {
        when(aiFacade.performSearch(any(AISearchRequest.class))).thenReturn(searchResponse);
        
        AISearchResponse result = aiEndpointService.processSearchRequest(searchRequest);
        
        assertNotNull(result);
        assertNotNull(result.getResults());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("user123", result.getRequestId());
        assertNotNull(result.getTimestamp());
        
        verify(aiFacade).performSearch(any(AISearchRequest.class));
    }
    
    @Test
    void testProcessSearchRequest_ValidationError() {
        AISearchRequest invalidRequest = AISearchRequest.builder()
            .query("") // Empty query should fail validation
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processSearchRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).performSearch(any());
    }
    
    @Test
    void testProcessQuickSearch_Success() {
        when(aiFacade.performSearch(any(AISearchRequest.class))).thenReturn(searchResponse);
        
        AISearchResponse result = aiEndpointService.processQuickSearch(
            "Test query", 10, "test-index", "user123");
        
        assertNotNull(result);
        assertEquals("Test query", result.getQuery());
        assertEquals("SUCCESS", result.getStatus());
        
        verify(aiFacade).performSearch(any(AISearchRequest.class));
    }
    
    // ==================== RAG Tests ====================
    
    @Test
    void testProcessRAGRequest_Success() {
        when(aiFacade.performRAG(any(RAGRequest.class))).thenReturn(ragResponse);
        
        RAGResponse result = aiEndpointService.processRAGRequest(ragRequest);
        
        assertNotNull(result);
        assertEquals("Test answer", result.getAnswer());
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("user123", result.getRequestId());
        assertNotNull(result.getTimestamp());
        
        verify(aiFacade).performRAG(any(RAGRequest.class));
    }
    
    @Test
    void testProcessRAGRequest_ValidationError() {
        RAGRequest invalidRequest = RAGRequest.builder()
            .question("") // Empty question should fail validation
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processRAGRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).performRAG(any());
    }
    
    @Test
    void testProcessRAGRequest_TooLongQuestion() {
        RAGRequest invalidRequest = RAGRequest.builder()
            .question("x".repeat(2001)) // Too long question
            .build();
        
        assertThrows(IllegalArgumentException.class, () -> {
            aiEndpointService.processRAGRequest(invalidRequest);
        });
        
        verify(aiFacade, never()).performRAG(any());
    }
    
    // ==================== Utility Tests ====================
    
    @Test
    void testGetServiceStatistics() {
        Map<String, Object> stats = aiEndpointService.getServiceStatistics();
        
        assertNotNull(stats);
        assertEquals("AIEndpointService", stats.get("serviceName"));
        assertEquals("1.0.0", stats.get("version"));
        assertEquals("ACTIVE", stats.get("status"));
        assertNotNull(stats.get("timestamp"));
    }
    
    @Test
    void testHealthCheck() {
        Map<String, Object> health = aiEndpointService.healthCheck();
        
        assertNotNull(health);
        assertEquals("AIEndpointService", health.get("service"));
        assertEquals("HEALTHY", health.get("status"));
        assertNotNull(health.get("timestamp"));
        assertNotNull(health.get("uptime"));
    }
}