package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * UserAIRecommendationResponse
 * 
 * Response DTO for user AI recommendation operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAIRecommendationResponse {
    
    private UUID userId;
    
    private String recommendations;
    
    private String preferences;
    
    private Integer totalRecommendations;
    
    private String recommendationType;
    
    private LocalDateTime generationTimestamp;
    
    private Double confidenceScore;
    
    private Map<String, Object> metadata;
}