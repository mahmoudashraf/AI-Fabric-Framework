package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UserAIInsightsResponse
 * 
 * Response DTO for user AI insights operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAIInsightsResponse {
    
    private UUID userId;
    
    private String insights;
    
    private String recommendations;
    
    private String patterns;
    
    private String anomalies;
    
    private Double behaviorScore;
    
    private Double recommendationScore;
    
    private Map<String, Object> statistics;
    
    private List<String> topInterests;
    
    private List<String> topCategories;
    
    private List<String> topBrands;
    
    private Long totalInteractions;
    
    private LocalDateTime lastActivityAt;
    
    private LocalDateTime analysisTimestamp;
    
    private String analysisType;
    
    private Map<String, Object> metadata;
}