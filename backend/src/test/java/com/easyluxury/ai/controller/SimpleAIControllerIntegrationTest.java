package com.easyluxury.ai.controller;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Simple AI Controller Integration Test
 * 
 * Integration tests for Simple AI Controller endpoints.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SimpleAIControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private AICoreService aiCoreService;
    
    @Test
    void testHealthCheck_Success() throws Exception {
        // Mock the AI service response
        AIGenerationResponse mockResponse = AIGenerationResponse.builder()
            .content("OK")
            .model("gpt-4o-mini")
            .build();
        
        when(aiCoreService.generateContent(any())).thenReturn(mockResponse);
        
        // Test the health check endpoint
        mockMvc.perform(get("/api/v1/ai/simple/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.message").value("AI service is operational"))
                .andExpect(jsonPath("$.response").value("OK"));
    }
    
    @Test
    void testHealthCheck_Failure() throws Exception {
        // Mock the AI service to throw an exception
        when(aiCoreService.generateContent(any())).thenThrow(new RuntimeException("Service unavailable"));
        
        // Test the health check endpoint
        mockMvc.perform(get("/api/v1/ai/simple/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.message").value("AI service is not operational: Service unavailable"));
    }
}