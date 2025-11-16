package com.ai.behavior.analytics;

import com.ai.behavior.config.BehaviorProperties;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternAnalyzer implements BehaviorAnalyzer {

    private final Clock clock;
    private final BehaviorProperties properties;

    @Override
    public BehaviorInsights analyze(UUID userId, List<BehaviorEvent> events) {
        if (events == null || events.isEmpty()) {
            return emptyInsights(userId);
        }

        List<BehaviorEvent> chronological = events.stream()
            .sorted(Comparator.comparing(BehaviorEvent::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        int totalEvents = chronological.size();
        long viewCount = countEvents(chronological, EventType.VIEW, EventType.NAVIGATION);
        long addToCart = countEvents(chronological, EventType.ADD_TO_CART);
        long purchase = countEvents(chronological, EventType.PURCHASE);

        boolean funnelDetected = viewCount > 0 && addToCart > 0 && purchase > 0;
        double completionRate = viewCount == 0 ? 0 : (double) purchase / viewCount;

        long eveningCount = chronological.stream()
            .filter(e -> Optional.ofNullable(e.getTimestamp())
                .map(LocalDateTime::toLocalTime)
                .map(time -> !time.isBefore(LocalTime.of(18, 0)) && !time.isAfter(LocalTime.of(22, 0)))
                .orElse(false))
            .count();

        long weekendCount = chronological.stream()
            .filter(e -> Optional.ofNullable(e.getTimestamp())
                .map(LocalDateTime::getDayOfWeek)
                .map(day -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                .orElse(false))
            .count();

        Map<String, Long> categories = extractMetadataCounts(chronological, "category");

        List<String> patterns = new ArrayList<>();
        if (funnelDetected) {
            patterns.add("frequent_buyer");
        }
        if ((double) eveningCount / totalEvents >= 0.6) {
            patterns.add("evening_shopper");
        }
        if ((double) weekendCount / totalEvents >= 0.5) {
            patterns.add("weekend_preference");
        }
        categories.entrySet().stream()
            .filter(entry -> entry.getValue() >= Math.max(3, totalEvents * 0.3))
            .map(entry -> "category_" + entry.getKey().toLowerCase())
            .forEach(patterns::add);

        Map<String, Double> scores = new HashMap<>();
        scores.put("engagement_score", Math.min(1.0, totalEvents / 100.0 + 0.2));
        scores.put("conversion_probability", Math.min(1.0, completionRate + addToCart * 0.01));
        scores.put("churn_risk", computeChurnRisk(chronological));

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("preferred_categories", categories.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList());

        String segment = determineSegment(patterns, scores);
        List<String> recommendations = buildRecommendations(patterns, scores);

        LocalDateTime now = LocalDateTime.now(clock);
        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(patterns)
            .scores(scores)
            .segment(segment)
            .preferences(preferences)
            .recommendations(recommendations)
            .analyzedAt(now)
            .analysisVersion("pattern-1.0")
            .validUntil(now.plus(properties.getInsights().getValidity()))
            .build();
    }

    @Override
    public String getAnalyzerType() {
        return "pattern";
    }

    private BehaviorInsights emptyInsights(UUID userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(List.of("insufficient_data"))
            .scores(Map.of("engagement_score", 0.0))
            .segment("unknown")
            .preferences(Map.of())
            .recommendations(List.of("Collect more behavioral data"))
            .analyzedAt(now)
            .validUntil(now.plus(properties.getInsights().getValidity()))
            .analysisVersion("pattern-1.0")
            .build();
    }

    private long countEvents(List<BehaviorEvent> events, EventType... types) {
        Set<EventType> set = Set.of(types);
        return events.stream()
            .filter(e -> e.getEventType() != null && set.contains(e.getEventType()))
            .count();
    }

    private Map<String, Long> extractMetadataCounts(List<BehaviorEvent> events, String key) {
        return events.stream()
            .map(BehaviorEvent::getMetadata)
            .filter(map -> map != null && map.containsKey(key))
            .map(map -> String.valueOf(map.get(key)))
            .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));
    }

    private double computeChurnRisk(List<BehaviorEvent> events) {
        LocalDateTime now = LocalDateTime.now(clock);
        long lastSevenDays = events.stream()
            .filter(e -> e.getTimestamp() != null && !e.getTimestamp().isBefore(now.minusDays(7)))
            .count();
        long previousSevenDays = events.stream()
            .filter(e -> e.getTimestamp() != null
                && e.getTimestamp().isBefore(now.minusDays(7))
                && !e.getTimestamp().isBefore(now.minusDays(14)))
            .count();
        if (previousSevenDays == 0) {
            return 0.3;
        }
        double trend = (double) lastSevenDays / previousSevenDays;
        if (trend < 0.3) {
            return 0.9;
        }
        if (trend < 0.7) {
            return 0.6;
        }
        return 0.2;
    }

    private String determineSegment(List<String> patterns, Map<String, Double> scores) {
        if (patterns.contains("frequent_buyer") && scores.getOrDefault("engagement_score", 0.0) > 0.7) {
            return "vip";
        }
        if (scores.getOrDefault("churn_risk", 0.0) > 0.7) {
            return "at_risk";
        }
        if (patterns.contains("evening_shopper")) {
            return "evening_focus";
        }
        return "active";
    }

    private List<String> buildRecommendations(List<String> patterns, Map<String, Double> scores) {
        List<String> recommendations = new ArrayList<>();
        if (patterns.contains("frequent_buyer")) {
            recommendations.add("offer_vip_upgrade");
        }
        if (patterns.contains("cart_abandoner")) {
            recommendations.add("send_cart_reminder");
        }
        if (scores.getOrDefault("churn_risk", 0.0) > 0.7) {
            recommendations.add("launch_reengagement_campaign");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("deliver_personalized_recommendations");
        }
        return recommendations;
    }
}
