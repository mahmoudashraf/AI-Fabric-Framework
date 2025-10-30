package com.easyluxury.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * OrderAIFraudDetectionRequest
 * 
 * Request DTO for order AI fraud detection operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAIFraudDetectionRequest {
    
    @NotNull(message = "Order ID is required")
    private UUID orderId;
    
    private Boolean includePatternAnalysis = true;
    
    private Boolean includeAnomalyDetection = true;
    
    private Boolean includeRiskAssessment = true;
    
    private Long timestamp = System.currentTimeMillis();
}