package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * OrderAIInsightsResponse
 * 
 * Response DTO for order AI insights operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAIInsightsResponse {
    
    private String insights;
    
    private String patterns;
    
    private String anomalies;
    
    private BigDecimal totalRevenue;
    
    private BigDecimal averageOrderValue;
    
    private Long totalOrders;
    
    private Long completedOrders;
    
    private Double completionRate;
    
    private Integer analysisPeriodDays;
    
    private LocalDateTime analysisTimestamp;
    
    private String insightsType;
    
    private Map<String, Object> metadata;
}