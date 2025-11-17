package com.ai.behavior.adapter;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter that keeps the legacy behavior API contract compatible with the new behavior module.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacySystemAdapter {

    private final ObjectMapper objectMapper;

    public BehaviorEvent toBehaviorEvent(BehaviorRequest request) {
        Map<String, Object> metadata = parseMetadata(request.getMetadata());
        addIfHasText(metadata, "action", request.getAction());
        addIfHasText(metadata, "context", request.getContext());
        addIfHasText(metadata, "deviceInfo", request.getDeviceInfo());
        addIfHasText(metadata, "locationInfo", request.getLocationInfo());
        if (request.getDurationSeconds() != null) {
            metadata.put("durationSeconds", request.getDurationSeconds());
        }
        addIfHasText(metadata, "value", request.getValue());

        return BehaviorEvent.builder()
            .userId(parseUuid(request.getUserId()))
            .sessionId(request.getSessionId())
            .eventType(parseEventType(request.getBehaviorType()))
            .entityType(request.getEntityType())
            .entityId(request.getEntityId())
            .timestamp(LocalDateTime.now())
            .metadata(metadata)
            .build();
    }

    public BehaviorResponse toBehaviorResponse(BehaviorEvent event) {
        return BehaviorResponse.builder()
            .id(event.getId() != null ? event.getId().toString() : null)
            .userId(event.getUserId() != null ? event.getUserId().toString() : null)
            .behaviorType(event.getEventType() != null ? event.getEventType().name() : null)
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .action(metadataValue(event, "action"))
            .context(metadataValue(event, "context"))
            .deviceInfo(metadataValue(event, "deviceInfo"))
            .locationInfo(metadataValue(event, "locationInfo"))
            .metadata(serializeMetadata(event.getMetadata()))
            .sessionId(event.getSessionId())
            .durationSeconds(parseLong(metadataValue(event, "durationSeconds")))
            .value(metadataValue(event, "value"))
            .createdAt(event.getTimestamp())
            .build();
    }

    private void addIfHasText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (JsonProcessingException ex) {
            log.debug("Failed to parse legacy metadata, storing raw value", ex);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", metadata);
            return fallback;
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return metadata.toString();
        }
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String metadataValue(BehaviorEvent event, String key) {
        if (event.getMetadata() == null || key == null) {
            return null;
        }
        Object value = event.getMetadata().get(key);
        return value != null ? value.toString() : null;
    }

    private EventType parseEventType(String behaviorType) {
        if (!StringUtils.hasText(behaviorType)) {
            return EventType.CUSTOM;
        }
        return switch (behaviorType.toUpperCase()) {
            case "PRODUCT_VIEW", "VIEW", "PAGE_VIEW", "RECOMMENDATION_VIEW" -> EventType.VIEW;
            case "NAVIGATION" -> EventType.NAVIGATION;
            case "CLICK" -> EventType.CLICK;
            case "SEARCH", "SEARCH_QUERY" -> EventType.SEARCH;
            case "FILTER", "SORT_CHANGE" -> EventType.FILTER;
            case "ADD_TO_CART" -> EventType.ADD_TO_CART;
            case "REMOVE_FROM_CART" -> EventType.REMOVE_FROM_CART;
            case "PURCHASE", "ORDER_CREATED", "PAYMENT_SUCCESS" -> EventType.PURCHASE;
            case "WISHLIST" -> EventType.WISHLIST;
            case "FEEDBACK" -> EventType.FEEDBACK;
            case "REVIEW" -> EventType.REVIEW;
            case "RATING" -> EventType.RATING;
            case "SHARE" -> EventType.SHARE;
            case "SAVE" -> EventType.SAVE;
            default -> EventType.CUSTOM;
        };
    }
}
