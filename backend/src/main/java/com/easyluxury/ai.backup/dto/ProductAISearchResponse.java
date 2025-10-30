package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ProductAISearchResponse
 * 
 * Response DTO for product AI search operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAISearchResponse {
    
    private String query;
    
    private List<Map<String, Object>> results;
    
    private Integer totalResults;
    
    private Double maxScore;
    
    private Long processingTimeMs;
    
    private String requestId;
    
    private String model;
    
    private LocalDateTime searchTimestamp;
    
    private Map<String, Object> metadata;
}