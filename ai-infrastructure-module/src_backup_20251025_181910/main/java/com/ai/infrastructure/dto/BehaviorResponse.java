package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Generic Behavior Response DTO
 * 
 * Response DTO for behavioral data operations.
 * This DTO is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorResponse {
    
    private String id;
    private String userId;
    private String behaviorType;
    private String entityType;
    private String entityId;
    private String action;
    private String context;
    private String metadata;
    private String sessionId;
    private String deviceInfo;
    private String locationInfo;
    private Long durationSeconds;
    private String value;
    private String aiAnalysis;
    private String aiInsights;
    private Double behaviorScore;
    private Double significanceScore;
    private String patternFlags;
    private LocalDateTime createdAt;
    private Map<String, Object> additionalData;
}