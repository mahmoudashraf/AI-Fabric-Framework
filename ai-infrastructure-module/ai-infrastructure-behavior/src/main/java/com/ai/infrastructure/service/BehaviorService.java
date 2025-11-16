package com.ai.infrastructure.service;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.ingestion.BehaviorEventValidator;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import com.ai.behavior.service.BehaviorAnalysisService;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.behavior.storage.BehaviorEventRepository;
import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legacy-compatible behavior service that forwards to the new ai-behavior module.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BehaviorService {

    private final BehaviorIngestionService ingestionService;
    private final BehaviorEventValidator validator;
    private final BehaviorEventRepository eventRepository;
    private final BehaviorDataProvider behaviorDataProvider;
    private final BehaviorAnalysisService analysisService;
    private final ObjectMapper objectMapper;

    public BehaviorResponse createBehavior(BehaviorRequest request) {
        BehaviorEvent event = toBehaviorEvent(request);
        validator.validate(event);
        BehaviorEvent stored = ingestionService.ingest(event);
        return toBehaviorResponse(stored);
    }

    @Transactional(readOnly = true)
    public BehaviorResponse getBehaviorById(UUID id) {
        return eventRepository.findById(id)
            .map(this::toBehaviorResponse)
            .orElseThrow(() -> new IllegalArgumentException("Behavior not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserId(UUID userId) {
        return eventRepository.findByUserIdOrderByTimestampDesc(userId).stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserIdAndType(UUID userId, com.ai.infrastructure.entity.Behavior.BehaviorType behaviorType) {
        EventType eventType = parseEventType(behaviorType.name());
        return behaviorDataProvider.query(BehaviorQuery.builder()
                .userId(userId)
                .eventType(eventType)
                .limit(500)
                .build())
            .stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<BehaviorResponse> getBehaviorsByUserId(UUID userId, Pageable pageable) {
        List<BehaviorResponse> responses = eventRepository.findRecentEvents(userId, pageable).stream()
            .map(this::toBehaviorResponse)
            .toList();
        return new PageImpl<>(responses, pageable, responses.size());
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByType(EventType eventType) {
        return behaviorDataProvider.query(BehaviorQuery.builder()
                .eventType(eventType)
                .limit(500)
                .build())
            .stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByEntity(String entityType, String entityId) {
        return queryForResponses(BehaviorQuery.builder()
            .entityType(entityType)
            .entityId(entityId)
            .limit(500)
            .build());
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsBySession(String sessionId) {
        return eventRepository.findBySessionIdOrderByTimestampDesc(sessionId).stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return behaviorDataProvider.query(BehaviorQuery.builder()
                .startTime(startDate)
                .endTime(endDate)
                .limit(1000)
                .build())
            .stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserIdAndDateRange(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
        return behaviorDataProvider.query(BehaviorQuery.builder()
                .userId(userId)
                .startTime(startDate)
                .endTime(endDate)
                .limit(1000)
                .build())
            .stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    public BehaviorResponse updateBehavior(UUID id, BehaviorRequest request) {
        BehaviorEvent existing = eventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Behavior not found: " + id));
        BehaviorEvent updated = toBehaviorEvent(request);
        updated.setId(existing.getId());
        updated.setIngestedAt(existing.getIngestedAt());
        validator.validate(updated);
        return toBehaviorResponse(eventRepository.save(updated));
    }

    public void deleteBehavior(UUID id) {
        eventRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public BehaviorAnalysisResult analyzeBehaviors(UUID userId) {
        var insights = analysisService.analyze(userId);
        return BehaviorAnalysisResult.builder()
            .analysisId(UUID.randomUUID().toString())
            .userId(userId.toString())
            .analysisType("behavior_pattern_detection")
            .summary("Analyzed " + eventRepository.findByUserIdOrderByTimestampDesc(userId).size() + " behavioral events")
            .insights(insights.getPatterns())
            .patterns(insights.getPatterns())
            .recommendations(insights.getRecommendations())
            .confidenceScore(insights.getScores().getOrDefault("engagement_score", 0.0))
            .significanceScore(insights.getScores().getOrDefault("conversion_probability", 0.0))
            .analyzedAt(insights.getAnalyzedAt())
            .build();
    }

    private BehaviorEvent toBehaviorEvent(BehaviorRequest request) {
        Map<String, Object> metadata = parseMetadata(request.getMetadata());
        if (StringUtils.hasText(request.getAction())) {
            metadata.put("action", request.getAction());
        }
        if (StringUtils.hasText(request.getContext())) {
            metadata.put("context", request.getContext());
        }
        if (StringUtils.hasText(request.getDeviceInfo())) {
            metadata.put("deviceInfo", request.getDeviceInfo());
        }
        if (StringUtils.hasText(request.getLocationInfo())) {
            metadata.put("locationInfo", request.getLocationInfo());
        }
        if (request.getDurationSeconds() != null) {
            metadata.put("durationSeconds", request.getDurationSeconds());
        }
        if (StringUtils.hasText(request.getValue())) {
            metadata.put("value", request.getValue());
        }
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

    private List<BehaviorResponse> queryForResponses(BehaviorQuery query) {
        return behaviorDataProvider.query(query).stream()
            .map(this::toBehaviorResponse)
            .toList();
    }

    private BehaviorResponse toBehaviorResponse(BehaviorEvent event) {
        return BehaviorResponse.builder()
            .id(event.getId() != null ? event.getId().toString() : null)
            .userId(event.getUserId() != null ? event.getUserId().toString() : null)
            .behaviorType(event.getEventType().name())
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .action(metadataValue(event, "action"))
            .context(metadataValue(event, "context"))
            .deviceInfo(metadataValue(event, "deviceInfo"))
            .locationInfo(metadataValue(event, "locationInfo"))
            .durationSeconds(parseLong(metadataValue(event, "durationSeconds")))
            .value(metadataValue(event, "value"))
            .metadata(serializeMetadata(event.getMetadata()))
            .sessionId(event.getSessionId())
            .createdAt(event.getTimestamp())
            .build();
    }

    private Map<String, Object> parseMetadata(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadata, Map.class);
        } catch (IOException ex) {
            log.debug("Failed to parse behavior metadata, storing as raw string");
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

    private EventType parseEventType(String behaviorType) {
        if (!StringUtils.hasText(behaviorType)) {
            return EventType.CUSTOM;
        }
        return switch (behaviorType.toUpperCase()) {
            case "PRODUCT_VIEW", "VIEW", "PAGE_VIEW", "RECOMMENDATION_VIEW" -> EventType.VIEW;
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

    private String metadataValue(BehaviorEvent event, String key) {
        if (event.getMetadata() == null || !event.getMetadata().containsKey(key)) {
            return null;
        }
        Object value = event.getMetadata().get(key);
        return value != null ? value.toString() : null;
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
}
