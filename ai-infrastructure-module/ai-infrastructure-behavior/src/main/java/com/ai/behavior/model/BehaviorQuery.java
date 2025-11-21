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
    String schemaId;
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
    Map<String, Object> attributeEquals = Collections.emptyMap();

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

    public BehaviorQuery schemaId(String schemaId) {
        return toBuilder().schemaId(schemaId).build();
    }

    public Map<String, Object> safeAttributeEquals() {
        return attributeEquals == null ? Collections.emptyMap() : attributeEquals;
    }

    public boolean hasTimeWindow() {
        return startTime != null || endTime != null;
    }
}
