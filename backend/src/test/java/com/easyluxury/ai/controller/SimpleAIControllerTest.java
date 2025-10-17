package com.easyluxury.ai.controller;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Simple AI Controller Test
 * 
 * Unit tests for Simple AI Controller endpoints.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@WebMvcTest(controllers = SimpleAIController.class)
@ActiveProfiles("test")
class SimpleAIControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AICoreService aiCoreService;
    
    private AIGenerationResponse generationResponse;
    private AIEmbeddingResponse embeddingResponse;
    private AISearchResponse searchResponse;
    
    @BeforeEach
    void setUp() {
        // Setup test data
        generationResponse = AIGenerationResponse.builder()
            .content("Generated content")
            .model("gpt-4o-mini")
            .build();
        
        embeddingResponse = AIEmbeddingResponse.builder()
            .embedding(List.of(0.1, 0.2, 0.3))
            .model("text-embedding-3-small")
            .dimensions(3)
            .build();
        
        searchResponse = AISearchResponse.builder()
            .results(List.of(Map.of("id", "1", "content", "Test result")))
            .totalResults(1)
            .build();
    }
    
    @Test
    void testGenerateContent_Success() throws Exception {
        when(aiCoreService.generateContent(any())).thenReturn(generationResponse);
        
        mockMvc.perform(post("/api/v1/ai/simple/generate")
                .param("prompt", "Test prompt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Generated content"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"));
    }
    
    @Test
    void testGenerateContent_WithSystemPrompt() throws Exception {
        when(aiCoreService.generateContent(any())).thenReturn(generationResponse);
        
        mockMvc.perform(post("/api/v1/ai/simple/generate")
                .param("prompt", "Test prompt")
                .param("systemPrompt", "You are a helpful assistant")
                .param("maxTokens", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Generated content"));
    }
    
    @Test
    void testGenerateEmbedding_Success() throws Exception {
        when(aiCoreService.generateEmbedding(any())).thenReturn(embeddingResponse);
        
        mockMvc.perform(post("/api/v1/ai/simple/embedding")
                .param("text", "Test text"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding").isArray())
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.dimensions").value(3));
    }
    
    @Test
    void testGenerateEmbedding_WithEntityType() throws Exception {
        when(aiCoreService.generateEmbedding(any())).thenReturn(embeddingResponse);
        
        mockMvc.perform(post("/api/v1/ai/simple/embedding")
                .param("text", "Test text")
                .param("entityType", "product"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding").isArray());
    }
    
    @Test
    void testPerformSearch_Success() throws Exception {
        when(aiCoreService.performSearch(any())).thenReturn(searchResponse);
        
        mockMvc.perform(post("/api/v1/ai/simple/search")
                .param("query", "Test query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.totalResults").value(1));
    }
    
    @Test
    void testPerformSearch_WithParameters() throws Exception {
        when(aiCoreService.performSearch(any())).thenReturn(searchResponse);
        
        mockMvc.perform(post("/api/v1/ai/simple/search")
                .param("query", "Test query")
                .param("entityType", "product")
                .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
    }
    
    @Test
    void testHealthCheck_Success() throws Exception {
        when(aiCoreService.generateContent(any())).thenReturn(generationResponse);
        
        mockMvc.perform(get("/api/v1/ai/simple/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("AI service is operational"))
                .andExpect(jsonPath("$.response").value("Generated content"));
    }
    
    @Test
    void testHealthCheck_Failure() throws Exception {
        when(aiCoreService.generateContent(any())).thenThrow(new RuntimeException("Service unavailable"));
        
        mockMvc.perform(get("/api/v1/ai/simple/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.message").value("AI service is not operational: Service unavailable"));
    }
}