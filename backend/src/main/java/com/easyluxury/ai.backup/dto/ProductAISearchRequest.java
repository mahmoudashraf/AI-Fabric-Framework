package com.easyluxury.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * ProductAISearchRequest
 * 
 * Request DTO for product AI search operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAISearchRequest {
    
    @NotBlank(message = "Search query is required")
    private String query;
    
    @Positive(message = "Limit must be positive")
    private Integer limit = 10;
    
    private Double threshold = 0.7;
    
    private Map<String, Object> filters;
    
    private String category;
    
    private String brand;
    
    private Double minPrice;
    
    private Double maxPrice;
    
    private Boolean inStock = true;
    
    private Boolean isLuxury = true;
    
    private String sortBy = "relevance";
    
    private String sortOrder = "desc";
}