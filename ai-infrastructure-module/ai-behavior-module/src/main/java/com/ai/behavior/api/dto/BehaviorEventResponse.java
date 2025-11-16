package com.ai.behavior.api.dto;

import com.ai.behavior.model.EventType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorEventResponse {
    UUID id;
    UUID userId;
    String sessionId;
    EventType eventType;
    String entityType;
    String entityId;
    LocalDateTime timestamp;
    Map<String, Object> metadata;
    LocalDateTime ingestedAt;
}
