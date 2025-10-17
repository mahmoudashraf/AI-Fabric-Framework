package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * OrderAIFraudDetectionResponse
 * 
 * Response DTO for order AI fraud detection operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAIFraudDetectionResponse {
    
    private UUID orderId;
    
    private String fraudAnalysis;
    
    private Double fraudScore;
    
    private String riskLevel;
    
    private Double anomalyScore;
    
    private String patternFlags;
    
    private List<String> recommendations;
    
    private Long processingTimeMs;
    
    private String requestId;
}