package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorSignal;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
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
    @NotBlank
    private String schemaId;
    private String signalKey;
    private String version;

    private String entityType;
    private String entityId;
    private String source;
    private String channel;
    private LocalDateTime timestamp;
    private Map<String, Object> attributes;

    public BehaviorSignal toEvent() {
        return BehaviorSignal.builder()
            .id(id)
            .userId(userId)
            .tenantId(tenantId)
            .sessionId(sessionId)
            .schemaId(schemaId)
            .signalKey(signalKey)
            .version(version)
            .entityType(entityType)
            .entityId(entityId)
            .source(source)
            .channel(channel)
            .timestamp(timestamp)
            .attributes(attributes)
            .build();
    }
}
