package com.ai.behavior.processing.analyzer;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    private static final String ANALYSIS_VERSION = "1.0.0";

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
                "engagement_score", 0.0,
                "conversion_probability", 0.1,
                "churn_risk", 0.5,
                "lifetime_value_estimate", 0.0
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
        long viewCount = events.stream().filter(e -> e.getEventType() == EventType.VIEW).count();
        long addToCart = events.stream().filter(e -> e.getEventType() == EventType.ADD_TO_CART).count();
        long purchases = events.stream().filter(e -> e.getEventType() == EventType.PURCHASE).count();
        long feedbacks = events.stream().filter(e -> e.getEventType() == EventType.FEEDBACK || e.getEventType() == EventType.REVIEW).count();

        double engagementScore = Math.min(1.0, Math.log(totalEvents + 1) / 5.0);
        double conversionProbability = viewCount == 0 ? 0.0 : (double) purchases / viewCount;
        double churnRisk = Math.max(0.05, 1.0 - engagementScore - conversionProbability / 2.0);
        double lifetimeValue = purchases * 250.0;

        scores.put("engagement_score", engagementScore);
        scores.put("conversion_probability", conversionProbability);
        scores.put("churn_risk", Math.min(1.0, churnRisk));
        scores.put("lifetime_value_estimate", lifetimeValue);
        scores.put("feedback_density", totalEvents == 0 ? 0.0 : (double) feedbacks / totalEvents);
        scores.put("cart_intent", totalEvents == 0 ? 0.0 : (double) addToCart / totalEvents);
        return scores;
    }

    private List<String> detectPatterns(List<BehaviorSignal> events, Map<String, Double> scores) {
        Set<String> patterns = new HashSet<>();
        long purchases = events.stream().filter(e -> e.getEventType() == EventType.PURCHASE).count();
        long addToCart = events.stream().filter(e -> e.getEventType() == EventType.ADD_TO_CART).count();
        long views = events.stream().filter(e -> e.getEventType() == EventType.VIEW).count();

        if (purchases >= 3) {
            patterns.add("frequent_buyer");
        }
        if (addToCart > purchases && addToCart > 0) {
            patterns.add("cart_abandoner");
        }
        if (isEveningShopper(events)) {
            patterns.add("evening_shopper");
        }
        if (isWeekendHeavy(events)) {
            patterns.add("weekend_loyalist");
        }
        if (hasHighSearchIntent(events)) {
            patterns.add("inspiration_seeker");
        }
        if (views > 0 && scores.getOrDefault("engagement_score", 0.0) < 0.2) {
            patterns.add("at_risk");
        }

        if (patterns.isEmpty()) {
            patterns.add("steady_state");
        }
        return new ArrayList<>(patterns);
    }

    private boolean isEveningShopper(List<BehaviorSignal> events) {
        long eveningEvents = events.stream()
            .filter(e -> e.getTimestamp() != null)
            .filter(e -> {
                int hour = e.getTimestamp().getHour();
                return hour >= 18 && hour <= 22;
            })
            .count();
        return !events.isEmpty() && (double) eveningEvents / events.size() >= 0.6;
    }

    private boolean isWeekendHeavy(List<BehaviorSignal> events) {
        long weekendEvents = events.stream()
            .filter(e -> e.getTimestamp() != null)
            .filter(e -> switch (e.getTimestamp().getDayOfWeek()) {
                case SATURDAY, SUNDAY -> true;
                default -> false;
            })
            .count();
        return !events.isEmpty() && (double) weekendEvents / events.size() >= 0.5;
    }

    private boolean hasHighSearchIntent(List<BehaviorSignal> events) {
        long searchEvents = events.stream()
            .filter(e -> e.getEventType() == EventType.SEARCH)
            .count();
        return !events.isEmpty() && (double) searchEvents / events.size() >= 0.25;
    }
}
