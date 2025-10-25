package com.ai.infrastructure.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * AIGenerationRequest
 * 
 * Request DTO for AI content generation operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIGenerationRequest {
    
    @NotNull(message = "Entity ID is required")
    private String entityId;
    
    @NotNull(message = "Entity type is required")
    private String entityType;
    
    @NotNull(message = "Generation type is required")
    private String generationType;
    
    private String prompt;
    
    private String context;
    
    private String systemPrompt;
    
    private String purpose;
    
    private Map<String, Object> parameters;
    
    private String model;
    
    private Integer maxTokens;
    
    private Double temperature;
    
    private String userId;
}