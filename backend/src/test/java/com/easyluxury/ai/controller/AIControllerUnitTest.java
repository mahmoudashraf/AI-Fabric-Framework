package com.easyluxury.ai.controller;

import com.easyluxury.ai.facade.AIFacade;
import com.easyluxury.ai.dto.AIGenerationRequest;
import com.easyluxury.ai.dto.AIGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AI Controller Unit Test
 * 
 * Unit tests for AI Controller without Spring context.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AIControllerUnitTest {
    
    @Mock
    private AIFacade aiFacade;
    
    @InjectMocks
    private AIController aiController;
    
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiController).build();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testGenerateContent_Success() throws Exception {
        // Mock the AI facade response
        AIGenerationResponse mockResponse = AIGenerationResponse.builder()
            .content("Generated content")
            .model("gpt-4o-mini")
            .status("SUCCESS")
            .build();
        
        when(aiFacade.generateContent(any(AIGenerationRequest.class))).thenReturn(mockResponse);
        
        // Test the generation endpoint
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Test prompt")
            .maxTokens(100)
            .build();
        
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Generated content"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    void testGenerateContent_ValidationError() throws Exception {
        // Test with empty prompt (should fail validation)
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("")  // Empty prompt should fail validation
            .maxTokens(100)
            .build();
        
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}