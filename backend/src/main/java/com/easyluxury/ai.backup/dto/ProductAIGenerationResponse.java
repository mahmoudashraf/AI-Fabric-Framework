package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * ProductAIGenerationResponse
 * 
 * Response DTO for product AI content generation operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAIGenerationResponse {
    
    private UUID productId;
    
    private String contentType;
    
    private String generatedContent;
    
    private String originalContent;
    
    private Double confidenceScore;
    
    private LocalDateTime generationTimestamp;
    
    private String model;
    
    private Long processingTimeMs;
    
    private String requestId;
    
    private Map<String, Object> metadata;
}