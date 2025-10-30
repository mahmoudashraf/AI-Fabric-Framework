package com.easyluxury.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * UserBehaviorResponse
 * 
 * Response DTO for user behavior tracking operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorResponse {
    
    private UUID id;
    
    private UUID userId;
    
    private String behaviorType;
    
    private String entityType;
    
    private String entityId;
    
    private String action;
    
    private String context;
    
    private Map<String, Object> metadata;
    
    private Long durationSeconds;
    
    private String value;
    
    private Double behaviorScore;
    
    private Double significanceScore;
    
    private String aiAnalysis;
    
    private String aiInsights;
    
    private String patternFlags;
    
    private LocalDateTime createdAt;
}