package com.ai.behavior.service;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.policy.BehaviorAnalysisPolicy;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalyzerService {

    private static final double DEFAULT_MAX_RECENCY_DAYS = 30.0;

    private final BehaviorAnalysisPolicy analysisPolicy;
    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorModuleProperties properties;
    private final BehaviorAuditService auditService;
    private final BehaviorMetricsService metricsService;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public BehaviorInsights analyzeUserBehavior(UUID userId, List<BehaviorEventEntity> events) {
        return metricsService.recordAnalysis(() -> executeAnalysis(userId, events));
    }

    private BehaviorInsights executeAnalysis(UUID userId, List<BehaviorEventEntity> events) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (CollectionUtils.isEmpty(events)) {
            throw new IllegalArgumentException("events must not be empty");
        }

        Map<String, Double> scores = calculateScores(events);
        List<String> patterns = analysisPolicy.detectPatterns(userId, events, scores);
        String segment = analysisPolicy.determineSegment(userId, events, scores);
        List<String> recommendations = analysisPolicy.generateRecommendations(userId, events, scores);
        double confidence = analysisPolicy.calculateConfidence(userId, events, scores);
        scores.put("confidenceScore", confidence);

        LocalDateTime analyzedAt = LocalDateTime.now(clock);
        LocalDateTime validUntil = analyzedAt.plusDays(Math.max(1, properties.getRetention().getInsightRetentionDays()));

        BehaviorInsights insights = BehaviorInsights.builder()
            .userId(userId)
            .patterns(patterns)
            .scores(scores)
            .segment(segment)
            .recommendations(recommendations)
            .preferences(Map.of())
            .analyzedAt(analyzedAt)
            .validUntil(validUntil)
            .analysisVersion("behavior-insight-v1")
            .build();

        BehaviorInsights saved = insightsRepository.save(insights);
        saved.notifyInsightsReady();
        auditService.logAnalysisCompleted(userId, saved);
        log.debug("Generated insights for user {} with segment {}", userId, segment);
        return saved;
    }

    private Map<String, Double> calculateScores(List<BehaviorEventEntity> events) {
        Map<String, Double> scores = new HashMap<>();
        double engagementScore = Math.min(1.0, events.size() / Math.max(1.0, properties.getEvents().getBatchSize() / 2.0));
        scores.put("engagementScore", engagementScore);

        OffsetDateTime newestEvent = events.stream()
            .map(BehaviorEventEntity::getCreatedAt)
            .filter(Objects::nonNull)
            .max(OffsetDateTime::compareTo)
            .orElse(OffsetDateTime.now(clock));

        double recencyScore = calculateRecencyScore(newestEvent);
        scores.put("recencyScore", recencyScore);

        double diversityScore = events.stream()
            .map(BehaviorEventEntity::getEventType)
            .filter(Objects::nonNull)
            .distinct()
            .count() / Math.max(1.0, events.size());
        scores.put("diversityScore", Math.min(1.0, diversityScore));
        return scores;
    }

    private double calculateRecencyScore(OffsetDateTime newestEvent) {
        if (newestEvent == null) {
            return 0.0;
        }
        double daysSinceLastEvent = Duration.between(newestEvent, OffsetDateTime.now(clock)).abs().toHours() / 24.0;
        double normalized = 1.0 - Math.min(1.0, daysSinceLastEvent / DEFAULT_MAX_RECENCY_DAYS);
        return Math.max(0.0, normalized);
    }
}
