package com.easyluxury.ai.dto;

import com.easyluxury.entity.Order;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OrderAIAnalysisResponse
 * 
 * Response DTO for order AI analysis operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAIAnalysisResponse {
    
    private UUID orderId;
    
    private UUID userId;
    
    private String analysis;
    
    private String patterns;
    
    private String anomalies;
    
    private Double fraudScore;
    
    private String riskLevel;
    
    private Double anomalyScore;
    
    private String patternFlags;
    
    private Map<String, Object> statistics;
    
    private List<String> riskFactors;
    
    private List<String> recommendations;
    
    private BigDecimal totalAmount;
    
    private String status;
    
    private String paymentMethod;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime analysisTimestamp;
    
    private String analysisType;
    
    private Map<String, Object> metadata;
}