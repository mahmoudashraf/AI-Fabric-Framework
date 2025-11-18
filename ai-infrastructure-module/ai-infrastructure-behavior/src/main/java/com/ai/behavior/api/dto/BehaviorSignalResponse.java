package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorSignal;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorSignalResponse {

    UUID id;
    UUID userId;
    String tenantId;
    String sessionId;
    String schemaId;
    String signalKey;
    String version;
    String entityType;
    String entityId;
    String source;
    String channel;
    LocalDateTime timestamp;
    LocalDateTime ingestedAt;
    Map<String, Object> attributes;

    public static BehaviorSignalResponse from(BehaviorSignal event) {
        return BehaviorSignalResponse.builder()
            .id(event.getId())
            .userId(event.getUserId())
            .tenantId(event.getTenantId())
            .sessionId(event.getSessionId())
            .schemaId(event.getSchemaId())
            .signalKey(event.getSignalKey())
            .version(event.getVersion())
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .source(event.getSource())
            .channel(event.getChannel())
            .timestamp(event.getTimestamp())
            .ingestedAt(event.getIngestedAt())
            .attributes(event.getAttributes())
            .build();
    }
}
