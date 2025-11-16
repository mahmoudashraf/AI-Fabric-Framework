package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class BehaviorQueryRequest {

    private UUID userId;
    private String sessionId;
    private String entityType;
    private String entityId;
    private EventType eventType;
    private List<EventType> eventTypes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Min(1)
    @Max(5000)
    private Integer limit = 200;
    private Integer offset = 0;
    private boolean orderDesc = true;
    private String providerType;

    public BehaviorQuery toQuery() {
        BehaviorQuery.BehaviorQueryBuilder builder = BehaviorQuery.builder()
            .userId(userId)
            .sessionId(sessionId)
            .entityType(entityType)
            .entityId(entityId)
            .eventType(eventType)
            .startTime(startTime)
            .endTime(endTime)
            .limit(limit)
            .offset(offset)
            .orderDesc(orderDesc);
        if (eventTypes != null) {
            builder.eventTypes(eventTypes);
        }
        return builder.build();
    }
}
