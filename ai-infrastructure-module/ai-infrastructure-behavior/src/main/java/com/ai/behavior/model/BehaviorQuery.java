package com.ai.behavior.model;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Fluent query object consumed by {@link com.ai.behavior.storage.BehaviorDataProvider}.
 */
@Value
@Builder(toBuilder = true)
public class BehaviorQuery {

    UUID userId;
    String sessionId;
    EventType eventType;
    String entityType;
    String entityId;
    LocalDateTime startTime;
    LocalDateTime endTime;
    @Builder.Default
    int limit = 100;
    @Builder.Default
    int offset = 0;
    @Builder.Default
    boolean ascending = false;
    @Builder.Default
    Map<String, Object> metadataEquals = Collections.emptyMap();

    public static BehaviorQuery forUser(UUID userId) {
        return BehaviorQuery.builder()
            .userId(userId)
            .limit(200)
            .build();
    }

    public static BehaviorQuery between(LocalDateTime start, LocalDateTime end) {
        return BehaviorQuery.builder()
            .startTime(start)
            .endTime(end)
            .limit(500)
            .build();
    }

    public BehaviorQuery limit(int limit) {
        return toBuilder().limit(limit).build();
    }

    public BehaviorQuery eventType(EventType type) {
        return toBuilder().eventType(type).build();
    }

    public Map<String, Object> safeMetadataEquals() {
        return metadataEquals == null ? Collections.emptyMap() : metadataEquals;
    }

    public boolean hasTimeWindow() {
        return startTime != null || endTime != null;
    }
}
