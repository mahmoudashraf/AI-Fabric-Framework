package com.easyluxury.ai.controller;

import com.easyluxury.ai.facade.AIFacade;
import com.easyluxury.ai.dto.AIGenerationRequest;
import com.easyluxury.ai.dto.AIGenerationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Simple AI Controller Test
 * 
 * Basic unit tests for AI Controller endpoints.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@WebMvcTest(AIController.class)
@ActiveProfiles("test")
@WithMockUser
class AIControllerSimpleTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AIFacade aiFacade;
    
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
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType("application/json")
                .content("{\"prompt\":\"Test prompt\",\"maxTokens\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Generated content"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
    
    @Test
    void testGenerateContent_ValidationError() throws Exception {
        // Test with empty prompt (should fail validation)
        mockMvc.perform(post("/api/v1/ai/generate")
                .contentType("application/json")
                .content("{\"prompt\":\"\",\"maxTokens\":100}"))
                .andExpect(status().isBadRequest());
    }
}