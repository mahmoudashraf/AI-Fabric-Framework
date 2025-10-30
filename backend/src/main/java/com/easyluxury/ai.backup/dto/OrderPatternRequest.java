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
 * OrderPatternRequest
 * 
 * Request DTO for order pattern analysis operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPatternRequest {
    
    private UUID userId;
    
    @Positive(message = "Days must be positive")
    private Integer days = 30;
    
    @Positive(message = "Months must be positive")
    private Integer months = 6;
    
    private String patternType = "comprehensive";
    
    private Boolean includeTemporalPatterns = true;
    
    private Boolean includeValuePatterns = true;
    
    private Boolean includeStatusPatterns = true;
    
    private Boolean includePaymentPatterns = true;
    
    private Boolean includeRiskPatterns = true;
    
    private Boolean includeSeasonalPatterns = false;
    
    private Map<String, Object> parameters;
}