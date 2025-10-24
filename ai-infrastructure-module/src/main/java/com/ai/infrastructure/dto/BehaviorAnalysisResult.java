package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Generic Behavior Analysis Result DTO
 * 
 * Result DTO for behavioral analysis operations.
 * This DTO is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorAnalysisResult {
    
    private String analysisId;
    private String userId;
    private String analysisType;
    private String summary;
    private List<String> insights;
    private List<String> patterns;
    private List<String> recommendations;
    private Double confidenceScore;
    private Double significanceScore;
    private Map<String, Object> metrics;
    private Map<String, Object> metadata;
    private LocalDateTime analyzedAt;
    private LocalDateTime validUntil;
}