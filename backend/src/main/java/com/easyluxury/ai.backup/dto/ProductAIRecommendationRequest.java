package com.easyluxury.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ProductAIRecommendationRequest
 * 
 * Request DTO for product AI recommendation operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAIRecommendationRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Positive(message = "Limit must be positive")
    private Integer limit = 10;
    
    private List<String> categories;
    
    private String priceRange;
    
    private List<UUID> excludeProductIds;
    
    private String recommendationType = "collaborative";
    
    private Boolean includeOutOfStock = false;
    
    private Boolean includeLuxuryOnly = true;
    
    private Map<String, Object> preferences;
}