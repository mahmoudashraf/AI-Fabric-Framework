package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AIHelperService
 * 
 * Helper service to simplify AI service calls and provide common patterns.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIHelperService {
    
    private final AICoreService aiCoreService;
    
    /**
     * Generate content with a simple prompt
     */
    public String generateContent(String prompt, String entityType, String purpose) {
        try {
            var response = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(prompt)
                    .model("gpt-4o-mini")
                    .maxTokens(1000)
                    .temperature(0.7)
                    .build()
            );
            return response.getContent();
        } catch (Exception e) {
            log.error("Failed to generate content with AI", e);
            throw new RuntimeException("AI content generation failed", e);
        }
    }
    
    /**
     * Generate content with default parameters
     */
    public String generateContent(String prompt) {
        return generateContent(prompt, "general", "content_generation");
    }
    
    /**
     * Generate content and return full AIGenerationResponse
     */
    public AIGenerationResponse generateContentResponse(String prompt, String entityType, String purpose) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(prompt)
                    .model("gpt-4o-mini")
                    .maxTokens(1000)
                    .temperature(0.7)
                    .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate content with AI", e);
            throw new RuntimeException("AI content generation failed", e);
        }
    }
    
    /**
     * Generate content and return full AIGenerationResponse with default parameters
     */
    public AIGenerationResponse generateContentResponse(String prompt) {
        return generateContentResponse(prompt, "general", "content_generation");
    }
}