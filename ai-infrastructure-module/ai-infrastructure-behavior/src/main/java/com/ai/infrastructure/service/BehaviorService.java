package com.ai.infrastructure.service;

import com.ai.behavior.adapter.LegacySystemAdapter;
import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.ingestion.BehaviorSignalValidator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import com.ai.behavior.service.BehaviorAnalysisService;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.behavior.storage.BehaviorSignalRepository;
import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
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
    private final BehaviorSignalValidator validator;
    private final BehaviorSignalRepository eventRepository;
    private final BehaviorDataProvider behaviorDataProvider;
    private final BehaviorAnalysisService analysisService;
    private final LegacySystemAdapter legacySystemAdapter;

    public BehaviorResponse createBehavior(BehaviorRequest request) {
        BehaviorSignal event = legacySystemAdapter.toBehaviorSignal(request);
        validator.validate(event);
        BehaviorSignal stored = ingestionService.ingest(event);
        return legacySystemAdapter.toBehaviorResponse(stored);
    }

    @Transactional(readOnly = true)
    public BehaviorResponse getBehaviorById(UUID id) {
        return eventRepository.findById(id)
              .map(legacySystemAdapter::toBehaviorResponse)
            .orElseThrow(() -> new IllegalArgumentException("Behavior not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserId(UUID userId) {
        return eventRepository.findByUserIdOrderByTimestampDesc(userId).stream()
            .map(legacySystemAdapter::toBehaviorResponse)
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
              .map(legacySystemAdapter::toBehaviorResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<BehaviorResponse> getBehaviorsByUserId(UUID userId, Pageable pageable) {
        List<BehaviorResponse> responses = eventRepository.findRecentEvents(userId, pageable).stream()
            .map(legacySystemAdapter::toBehaviorResponse)
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
              .map(legacySystemAdapter::toBehaviorResponse)
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
            .map(legacySystemAdapter::toBehaviorResponse)
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
              .map(legacySystemAdapter::toBehaviorResponse)
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
              .map(legacySystemAdapter::toBehaviorResponse)
            .toList();
    }

    public BehaviorResponse updateBehavior(UUID id, BehaviorRequest request) {
        BehaviorSignal existing = eventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Behavior not found: " + id));
        BehaviorSignal updated = legacySystemAdapter.toBehaviorSignal(request);
        updated.setId(existing.getId());
        updated.setIngestedAt(existing.getIngestedAt());
        validator.validate(updated);
        return legacySystemAdapter.toBehaviorResponse(eventRepository.save(updated));
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

    private List<BehaviorResponse> queryForResponses(BehaviorQuery query) {
        return behaviorDataProvider.query(query).stream()
            .map(legacySystemAdapter::toBehaviorResponse)
            .toList();
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
}
