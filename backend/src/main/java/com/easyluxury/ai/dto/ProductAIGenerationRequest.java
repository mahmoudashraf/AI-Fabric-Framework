package com.easyluxury.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * ProductAIGenerationRequest
 * 
 * Request DTO for product AI content generation operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAIGenerationRequest {
    
    @NotNull(message = "Product ID is required")
    private UUID productId;
    
    @NotNull(message = "Content type is required")
    private String contentType; // DESCRIPTION, TAGS, CATEGORIES, FEATURES, SEO_DESCRIPTION
    
    private String prompt;
    
    private String context;
    
    private Integer maxTokens = 200;
    
    private Double temperature = 0.7;
    
    private Boolean overwriteExisting = false;
    
    private Map<String, Object> parameters;
}