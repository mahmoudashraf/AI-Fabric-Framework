package com.ai.behavior.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class BehaviorEventRequest {

    private UUID userId;
    private String sessionId;

    @NotBlank
    private String eventType;

    private String entityType;
    private String entityId;

    private LocalDateTime timestamp;

    private Map<String, Object> metadata;
}
