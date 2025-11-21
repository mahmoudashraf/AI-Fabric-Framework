package com.easyluxury.ai.dto;

import com.easyluxury.entity.UserBehavior;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for user behavior tracking operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Behavior type is required")
    private UserBehavior.BehaviorType behaviorType;

    private String entityType;

    private String entityId;

    private String action;

    private String context;

    private Map<String, Object> metadata;

    private Long durationSeconds;

    private String value;
}
