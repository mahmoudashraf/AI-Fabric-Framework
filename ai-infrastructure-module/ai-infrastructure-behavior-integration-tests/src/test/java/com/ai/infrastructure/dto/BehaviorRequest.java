package com.ai.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Generic Behavior Request DTO
 * 
 * Request DTO for behavioral data operations.
 * This DTO is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorRequest {
    
    @NotNull(message = "User ID is required")
    private String userId;
    
    @NotNull(message = "Behavior type is required")
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
    private Map<String, Object> additionalData;
}