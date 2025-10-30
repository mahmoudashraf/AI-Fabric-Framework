package com.easyluxury.ai.dto;

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
 * OrderPatternResponse
 * 
 * Response DTO for order pattern analysis operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPatternResponse {
    
    private UUID userId;
    
    private String patterns;
    
    private String anomalies;
    
    private String seasonalPatterns;
    
    private Map<String, Object> temporalPatterns;
    
    private Map<String, Object> valuePatterns;
    
    private Map<String, Object> statusPatterns;
    
    private Map<String, Object> paymentPatterns;
    
    private Map<String, Object> riskPatterns;
    
    private Map<String, Object> statistics;
    
    private List<String> topCategories;
    
    private List<String> topPaymentMethods;
    
    private List<String> riskFactors;
    
    private BigDecimal averageOrderValue;
    
    private BigDecimal totalRevenue;
    
    private Long totalOrders;
    
    private Double completionRate;
    
    private Double cancellationRate;
    
    private LocalDateTime analysisTimestamp;
    
    private String patternType;
    
    private Map<String, Object> metadata;
}