package com.easyluxury.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * UserAIRecommendationRequest
 * 
 * Request DTO for user AI recommendation operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAIRecommendationRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Positive(message = "Limit must be positive")
    private Integer limit = 10;
    
    @Size(max = 50, message = "Recommendation type must not exceed 50 characters")
    private String recommendationType = "personalized";
    
    @Size(max = 10, message = "Categories list must not exceed 10 items")
    private List<String> categories;
    
    @Size(max = 20, message = "Price range must not exceed 20 characters")
    private String priceRange;
    
    private Boolean includeOutOfStock = false;
    
    private Map<String, Object> preferences;
    
    private Map<String, Object> filters;
}