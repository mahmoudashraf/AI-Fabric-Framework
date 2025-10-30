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
 * OrderAIAnalysisRequest
 * 
 * Request DTO for order AI analysis operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAIAnalysisRequest {
    
    private UUID orderId;
    
    private UUID userId;
    
    @Positive(message = "Days must be positive")
    private Integer days = 30;
    
    private String analysisType = "comprehensive";
    
    private Boolean includeFraudDetection = true;
    
    private Boolean includePatternAnalysis = true;
    
    private Boolean includeAnomalyDetection = false;
    
    private Map<String, Object> parameters;
}