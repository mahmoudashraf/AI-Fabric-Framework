package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorEvent;
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
    String tenantId;
    String sessionId;
    EventType eventType;
    String entityType;
    String entityId;
    String source;
    String channel;
    LocalDateTime timestamp;
    LocalDateTime ingestedAt;
    Map<String, Object> metadata;

    public static BehaviorEventResponse from(BehaviorEvent event) {
        return BehaviorEventResponse.builder()
            .id(event.getId())
            .userId(event.getUserId())
            .tenantId(event.getTenantId())
            .sessionId(event.getSessionId())
            .eventType(event.getEventType())
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .source(event.getSource())
            .channel(event.getChannel())
            .timestamp(event.getTimestamp())
            .ingestedAt(event.getIngestedAt())
            .metadata(event.getMetadata())
            .build();
    }
}
