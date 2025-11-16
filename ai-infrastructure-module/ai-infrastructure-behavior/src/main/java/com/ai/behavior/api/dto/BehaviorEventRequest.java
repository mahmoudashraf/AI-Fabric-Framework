package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BehaviorEventRequest {

    private UUID id;
    private UUID userId;
    private String tenantId;
    private String sessionId;

    @NotNull
    private EventType eventType;

    private String entityType;
    private String entityId;
    private String source;
    private String channel;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public BehaviorEvent toEvent() {
        return BehaviorEvent.builder()
            .id(id)
            .userId(userId)
            .tenantId(tenantId)
            .sessionId(sessionId)
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .source(source)
            .channel(channel)
            .timestamp(timestamp)
            .metadata(metadata)
            .build();
    }
}
