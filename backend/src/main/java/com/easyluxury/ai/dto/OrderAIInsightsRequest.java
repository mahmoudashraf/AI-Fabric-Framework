package com.easyluxury.ai.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * OrderAIInsightsRequest
 * 
 * Request DTO for order AI insights operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAIInsightsRequest {
    
    @Positive(message = "Days must be positive")
    private Integer days = 30;
    
    private String insightsType = "comprehensive";
    
    private Boolean includePatterns = true;
    
    private Boolean includeAnomalies = false;
    
    private Boolean includeSeasonal = false;
    
    private Map<String, Object> parameters;
}