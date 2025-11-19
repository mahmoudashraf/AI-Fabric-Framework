package com.ai.behavior.processing.analyzer;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.metrics.KpiKeys;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PatternAnalyzer implements BehaviorAnalyzer {

    private static final String ANALYSIS_VERSION = "2.0.0";

    private final BehaviorModuleProperties properties;
    private final SegmentationAnalyzer segmentationAnalyzer;

    @Override
    public BehaviorInsights analyze(UUID userId, List<BehaviorSignal> events) {
        if (events == null || events.isEmpty()) {
            return emptyInsights(userId);
        }

        events.sort(Comparator.comparing(BehaviorSignal::getTimestamp, Comparator.nullsLast(LocalDateTime::compareTo)));
        Map<String, Double> scores = computeScores(events);
        List<String> patterns = detectPatterns(events, scores);
        SegmentationAnalyzer.SegmentationSnapshot snapshot = segmentationAnalyzer.fromEvents(events, scores, patterns);

        LocalDateTime analyzedAt = LocalDateTime.now();
        LocalDateTime validUntil = analyzedAt.plus(properties.getInsights().getValidity());

        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(patterns)
            .scores(scores)
            .segment(snapshot.segment())
            .preferences(snapshot.preferences())
            .recommendations(snapshot.recommendations())
            .analyzedAt(analyzedAt)
            .validUntil(validUntil)
            .analysisVersion(ANALYSIS_VERSION)
            .build();
    }

    @Override
    public String getAnalyzerType() {
        return "pattern";
    }

    public BehaviorInsights emptyInsights(UUID userId) {
        LocalDateTime analyzedAt = LocalDateTime.now();
        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(List.of("insufficient_data"))
            .scores(Map.of(
                KpiKeys.ENGAGEMENT_SCORE, 0.0,
                KpiKeys.ENGAGEMENT_INTERACTION_DENSITY, 0.0,
                KpiKeys.ENGAGEMENT_VELOCITY, 0.0,
                KpiKeys.RECENCY_SCORE, 0.0,
                KpiKeys.RECENCY_HOURS_SINCE_LAST, 0.0,
                KpiKeys.DIVERSITY_SCORE, 0.0
            ))
            .segment("new_user")
            .preferences(Map.of())
            .recommendations(List.of("collect_additional_signals"))
            .analyzedAt(analyzedAt)
            .validUntil(analyzedAt.plus(properties.getInsights().getValidity()))
            .analysisVersion(ANALYSIS_VERSION)
            .build();
    }

    private Map<String, Double> computeScores(List<BehaviorSignal> events) {
        Map<String, Double> scores = new HashMap<>();
        long totalEvents = events.size();
        double engagementScore = Math.min(1.0, Math.log(totalEvents + 1) / 4.0);
        long uniqueSchemas = events.stream()
            .map(BehaviorSignal::getSchemaId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .count();
        double diversityScore = totalEvents == 0 ? 0.0 :
            Math.min(1.0, uniqueSchemas / Math.max(1.0d, totalEvents));

        LocalDateTime mostRecent = events.stream()
            .map(BehaviorSignal::getTimestamp)
            .filter(t -> t != null)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now().minusWeeks(1));
        double recencyHours = Duration.between(mostRecent, LocalDateTime.now()).toHours();
        double recencyScore = Math.max(0.0, 1.0 - (recencyHours / 168.0));

        long lastHourCount = events.stream()
            .map(BehaviorSignal::getTimestamp)
            .filter(t -> t != null && Duration.between(t, LocalDateTime.now()).toMinutes() <= 60)
            .count();
        double velocityScore = Math.min(1.0, lastHourCount / 20.0);
        double interactionDensity = Math.min(1.0, totalEvents / 100.0);

        scores.put(KpiKeys.ENGAGEMENT_SCORE, engagementScore);
        scores.put(KpiKeys.ENGAGEMENT_INTERACTION_DENSITY, interactionDensity);
        scores.put(KpiKeys.ENGAGEMENT_VELOCITY, velocityScore);
        scores.put(KpiKeys.DIVERSITY_SCORE, diversityScore);
        scores.put(KpiKeys.DIVERSITY_UNIQUE_SCHEMA_COUNT, (double) uniqueSchemas);
        scores.put(KpiKeys.RECENCY_SCORE, recencyScore);
        scores.put(KpiKeys.RECENCY_HOURS_SINCE_LAST, recencyHours);
        return scores;
    }

    private List<String> detectPatterns(List<BehaviorSignal> events, Map<String, Double> scores) {
        Set<String> patterns = new HashSet<>();
        double engagement = scores.getOrDefault(KpiKeys.ENGAGEMENT_SCORE, 0.0);
        double recency = scores.getOrDefault(KpiKeys.RECENCY_SCORE, 0.0);
        double velocity = scores.getOrDefault(KpiKeys.ENGAGEMENT_VELOCITY, 0.0);

        if (engagement >= 0.8) {
            patterns.add("power_user");
        } else if (engagement < 0.2) {
            patterns.add("low_activity");
        } else {
            patterns.add("steady_state");
        }

        if (recency < 0.3) {
            patterns.add("dormant");
        } else if (recency > 0.7) {
            patterns.add("recent_engagement");
        }

        if (velocity > 0.6) {
            patterns.add("burst_activity");
        }
        if (isEveningHeavy(events)) {
            patterns.add("evening_bias");
        }
        if (isWeekendHeavy(events)) {
            patterns.add("weekend_bias");
        }
        return new ArrayList<>(patterns);
    }

    private boolean isEveningHeavy(List<BehaviorSignal> events) {
        long eveningEvents = events.stream()
            .map(BehaviorSignal::getTimestamp)
            .filter(timestamp -> timestamp != null)
            .filter(timestamp -> {
                int hour = timestamp.getHour();
                return hour >= 18 || hour < 4;
            })
            .count();
        return !events.isEmpty() && (double) eveningEvents / events.size() >= 0.6;
    }

    private boolean isWeekendHeavy(List<BehaviorSignal> events) {
        long weekendEvents = events.stream()
            .map(BehaviorSignal::getTimestamp)
            .filter(timestamp -> timestamp != null)
            .filter(timestamp -> switch (timestamp.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> true;
                default -> false;
            })
            .count();
        return !events.isEmpty() && (double) weekendEvents / events.size() >= 0.5;
    }
}
