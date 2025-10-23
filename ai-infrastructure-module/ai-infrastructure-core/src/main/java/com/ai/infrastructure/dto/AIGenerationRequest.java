package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for AI content generation
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AIGenerationRequest {
    
    @NotBlank(message = "Prompt cannot be blank")
    @Size(max = 4000, message = "Prompt cannot exceed 4000 characters")
    private String prompt;
    
    private String systemPrompt;
    
    private String model;
    
    private Integer maxTokens;
    
    private Double temperature;
    
    private String context;
    
    private String entityType;
    
    private String purpose;
}