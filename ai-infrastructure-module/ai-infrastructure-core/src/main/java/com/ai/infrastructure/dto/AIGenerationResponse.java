package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for AI content generation
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
public class AIGenerationResponse {
    
    private String content;
    
    private String model;
    
    private Object usage;
    
    private Long processingTimeMs;
    
    private String requestId;
}