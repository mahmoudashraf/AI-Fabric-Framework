package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorEventRequest;
import com.ai.behavior.api.dto.BehaviorEventResponse;
import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class BehaviorEventMapper {

    public BehaviorEvent toEntity(BehaviorEventRequest request) {
        EventType eventType;
        try {
            eventType = EventType.valueOf(request.getEventType().toUpperCase());
        } catch (Exception ex) {
            throw new BehaviorValidationException("Unsupported event type: " + request.getEventType(), ex);
        }

        return BehaviorEvent.builder()
            .userId(request.getUserId())
            .sessionId(request.getSessionId())
            .eventType(eventType)
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .timestamp(request.getTimestamp())
            .metadata(request.getMetadata() != null ? new HashMap<>(request.getMetadata()) : new HashMap<>())
            .build();
    }

    public BehaviorEventResponse toResponse(BehaviorEvent event) {
        return BehaviorEventResponse.builder()
            .id(event.getId())
            .userId(event.getUserId())
            .sessionId(event.getSessionId())
            .eventType(event.getEventType())
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .timestamp(event.getTimestamp())
            .metadata(event.getMetadata())
            .ingestedAt(event.getIngestedAt())
            .build();
    }
}
