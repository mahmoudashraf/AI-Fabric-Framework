package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Compatibility DTO retained for consumers that still depend on the legacy behavior API surface.
 */
@Data
@Builder
public class BehaviorRequest {
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
}
