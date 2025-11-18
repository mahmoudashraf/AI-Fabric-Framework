package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorQuery;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class BehaviorQueryRequest {
    private String schemaId;
    private String entityType;
    private String entityId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int limit = 100;
    private int offset = 0;
    private boolean ascending = false;
    private Map<String, Object> attributeEquals;

    public BehaviorQuery toQuery() {
        return BehaviorQuery.builder()
            .schemaId(schemaId)
            .entityType(entityType)
            .entityId(entityId)
            .startTime(startTime)
            .endTime(endTime)
            .limit(limit)
            .offset(offset)
            .ascending(ascending)
            .attributeEquals(attributeEquals)
            .build();
    }
}
