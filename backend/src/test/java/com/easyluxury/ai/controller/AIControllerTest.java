package com.easyluxury.ai.controller;

import com.easyluxury.ai.dto.*;
import com.easyluxury.ai.facade.AIFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI Controller Test
 * 
 * Unit tests for AI Controller endpoints.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@WebMvcTest(AIController.class)
@ActiveProfiles("test")
class AIControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AIFacade aiFacade;
    
    @Autowired
    private ObjectMapper objectMapper;
    
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
            .build();
        
        generationResponse = AIGenerationResponse.builder()
            .content("Generated content")
            .model("gpt-4o-mini")
            .timestamp(LocalDateTime.now())
            .generationId("gen_123")
            .requestId("req_123")
            .status("SUCCESS")
            .processingTimeMs(1000L)
            .build();
        
        embeddingRequest = AIEmbeddingRequest.builder()
            .input("Test input")
            .model("text-embedding-3-small")
            .build();
        
        embeddingResponse = AIEmbeddingResponse.builder()
            .embeddings(List.of(List.of(0.1, 0.2, 0.3)))
            .model("text-embedding-3-small")
            .dimensions(3)
            .timestamp(LocalDateTime.now())
            .embeddingId("emb_123")
            .requestId("req_123")
            .status("SUCCESS")
            .processingTimeMs(500L)
            .input("Test input")
            .build();
        
        searchRequest = AISearchRequest.builder()
            .query("Test query")
            .limit(10)
            .build();
        
        searchResponse = AISearchResponse.builder()
            .results(List.of())
            .totalResults(0L)
            .returnedResults(0)
            .query("Test query")
            .timestamp(LocalDateTime.now())
            .searchId("search_123")
            .requestId("req_123")
            .status("SUCCESS")
            .processingTimeMs(300L)
            .build();
        
        ragRequest = RAGRequest.builder()
            .question("Test question")
            .knowledgeBase("test")
            .build();
        
        ragResponse = RAGResponse.builder()
            .answer("Test answer")
            .question("Test question")
            .knowledgeBase("test")
            .timestamp(LocalDateTime.now())
            .ragId("rag_123")
            .requestId("req_123")
            .status("SUCCESS")
            .processingTimeMs(2000L)
            .confidence(0.9)
            .documentsRetrieved(3)
            .documentsUsed(2)
            .build();
    }
    
    // ==================== AI Generation Tests ====================
    
    @Test
    void testGenerateContent_Success() throws Exception {
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(generationResponse);
        
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(generationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Generated content"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.generationId").value("gen_123"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    void testGenerateContent_ValidationError() throws Exception {
        AIGenerationRequest invalidRequest = AIGenerationRequest.builder()
            .prompt("") // Empty prompt should fail validation
            .build();
        
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testQuickGenerate_Success() throws Exception {
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(generationResponse);
        
        mockMvc.perform(get("/api/v1/ai/generate/quick")
                .param("prompt", "Test prompt")
                .param("maxTokens", "100")
                .param("temperature", "0.7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Generated content"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    // ==================== AI Embedding Tests ====================
    
    @Test
    void testGenerateEmbeddings_Success() throws Exception {
        when(aiFacade.generateEmbeddings(any(AIEmbeddingRequest.class))).thenReturn(embeddingResponse);
        
        mockMvc.perform(post("/api/v1/ai/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(embeddingRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddings").isArray())
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.embeddingId").value("emb_123"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    void testGenerateEmbeddings_ValidationError() throws Exception {
        AIEmbeddingRequest invalidRequest = AIEmbeddingRequest.builder()
            .input("") // Empty input should fail validation
            .build();
        
        mockMvc.perform(post("/api/v1/ai/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
    
    // ==================== AI Search Tests ====================
    
    @Test
    void testPerformSearch_Success() throws Exception {
        when(aiFacade.performSearch(any(AISearchRequest.class))).thenReturn(searchResponse);
        
        mockMvc.perform(post("/api/v1/ai/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(searchRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.query").value("Test query"))
                .andExpect(jsonPath("$.searchId").value("search_123"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    void testQuickSearch_Success() throws Exception {
        when(aiFacade.performSearch(any(AISearchRequest.class))).thenReturn(searchResponse);
        
        mockMvc.perform(get("/api/v1/ai/search/quick")
                .param("query", "Test query")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Test query"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    // ==================== RAG Tests ====================
    
    @Test
    void testPerformRAG_Success() throws Exception {
        when(aiFacade.performRAG(any(RAGRequest.class))).thenReturn(ragResponse);
        
        mockMvc.perform(post("/api/v1/ai/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ragRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Test answer"))
                .andExpect(jsonPath("$.question").value("Test question"))
                .andExpect(jsonPath("$.ragId").value("rag_123"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    void testPerformRAG_ValidationError() throws Exception {
        RAGRequest invalidRequest = RAGRequest.builder()
            .question("") // Empty question should fail validation
            .build();
        
        mockMvc.perform(post("/api/v1/ai/rag")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }
    
    // ==================== Error Handling Tests ====================
    
    @Test
    void testGenerateContent_Error() throws Exception {
        AIGenerationResponse errorResponse = AIGenerationResponse.builder()
            .content("")
            .status("ERROR")
            .errorMessage("Generation failed")
            .build();
        
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(errorResponse);
        
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(generationRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.errorMessage").value("Generation failed"));
    }
}