package com.easyluxury.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * UserAIInsightsRequest
 * 
 * Request DTO for user AI insights operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAIInsightsRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @Positive(message = "Days must be positive")
    private Integer days = 30;
    
    private String analysisType = "behavioral";
    
    private Map<String, Object> parameters;
    
    private Boolean includeRecommendations = true;
    
    private Boolean includePatterns = true;
    
    private Boolean includeAnomalies = false;
}