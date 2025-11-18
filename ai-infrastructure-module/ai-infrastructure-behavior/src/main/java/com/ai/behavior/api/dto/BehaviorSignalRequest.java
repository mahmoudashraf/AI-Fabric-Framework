package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.EventType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BehaviorSignalRequest {

    private UUID id;
    private UUID userId;
    private String tenantId;
    private String sessionId;
    private String schemaId;
    private String signalKey;
    private String version;

    @NotNull
    private EventType eventType;

    private String entityType;
    private String entityId;
    private String source;
    private String channel;
    private LocalDateTime timestamp;
    private Map<String, Object> attributes;
    private Map<String, Object> metadata;

    public BehaviorSignal toEvent() {
        Map<String, Object> payload = attributes != null ? attributes : metadata;
        return BehaviorSignal.builder()
            .id(id)
            .userId(userId)
            .tenantId(tenantId)
            .sessionId(sessionId)
            .schemaId(resolveSchemaId())
            .signalKey(signalKey)
            .version(version)
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .source(source)
            .channel(channel)
            .timestamp(timestamp)
            .attributes(payload)
            .build();
    }

    private String resolveSchemaId() {
        if (schemaId != null && !schemaId.isBlank()) {
            return schemaId;
        }
        return eventType != null ? "legacy." + eventType.name().toLowerCase() : null;
    }
}
