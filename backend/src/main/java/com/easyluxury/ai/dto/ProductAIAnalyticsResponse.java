package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ProductAIAnalyticsResponse
 * 
 * Response DTO for product AI analytics operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAIAnalyticsResponse {
    
    private UUID productId;
    
    private String analytics;
    
    private Long viewCount;
    
    private Long purchaseCount;
    
    private Double recommendationScore;
    
    private LocalDateTime lastViewedAt;
    
    private LocalDateTime lastPurchasedAt;
    
    private Long processingTimeMs;
    
    private String requestId;
}