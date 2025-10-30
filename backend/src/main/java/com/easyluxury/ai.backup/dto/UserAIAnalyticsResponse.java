package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * UserAIAnalyticsResponse
 * 
 * Response DTO for user AI analytics operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAIAnalyticsResponse {
    
    private UUID userId;
    
    private String patterns;
    
    private String anomalies;
    
    private String insights;
    
    private Double behaviorScore;
    
    private Map<String, Object> statistics;
    
    private Integer analysisDays;
    
    private Long processingTimeMs;
    
    private String requestId;
}