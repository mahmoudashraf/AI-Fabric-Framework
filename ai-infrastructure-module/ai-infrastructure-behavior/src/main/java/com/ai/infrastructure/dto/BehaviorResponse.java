package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Compatibility DTO mirroring the previous behavior response structure.
 */
@Value
@Builder
public class BehaviorResponse {
    String id;
    String userId;
    String behaviorType;
    String entityType;
    String entityId;
    String action;
    String context;
    String metadata;
    String sessionId;
    String deviceInfo;
    String locationInfo;
    Long durationSeconds;
    String value;
    String aiAnalysis;
    String aiInsights;
    Double behaviorScore;
    Double significanceScore;
    String patternFlags;
    LocalDateTime createdAt;
}
