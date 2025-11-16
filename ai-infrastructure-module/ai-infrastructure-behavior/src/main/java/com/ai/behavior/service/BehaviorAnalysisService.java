package com.ai.behavior.service;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorAnalysisException;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.EventType;
import com.ai.behavior.storage.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {

    private static final String ANALYSIS_VERSION = "1.0.0";

    private final BehaviorEventRepository eventRepository;
    private final BehaviorModuleProperties properties;

    @Transactional(readOnly = true)
    public BehaviorInsights analyze(UUID userId) {
        try {
            List<BehaviorEvent> events = eventRepository.findTop200ByUserIdOrderByTimestampDesc(userId);
            if (events.isEmpty()) {
                return emptyInsights(userId);
            }
            events.sort(Comparator.comparing(BehaviorEvent::getTimestamp));
            Map<String, Double> scores = computeScores(events);
            List<String> patterns = detectPatterns(events, scores);
            Map<String, Object> preferences = detectPreferences(events);
            List<String> recommendations = buildRecommendations(patterns, scores, preferences);
            String segment = determineSegment(patterns, scores);

            return BehaviorInsights.builder()
                .userId(userId)
                .patterns(patterns)
                .scores(scores)
                .segment(segment)
                .preferences(preferences)
                .recommendations(recommendations)
                .analyzedAt(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plus(properties.getInsights().getValidity()))
                .analysisVersion(ANALYSIS_VERSION)
                .build();
        } catch (Exception ex) {
            throw new BehaviorAnalysisException("Failed to analyze behavior for user " + userId, ex);
        }
    }

    private BehaviorInsights emptyInsights(UUID userId) {
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
            .analyzedAt(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plus(properties.getInsights().getValidity()))
            .analysisVersion(ANALYSIS_VERSION)
            .build();
    }

    private Map<String, Double> computeScores(List<BehaviorEvent> events) {
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
        scores.put("feedback_density", viewCount == 0 ? 0.0 : (double) feedbacks / totalEvents);
        scores.put("cart_intent", addToCart == 0 ? 0.0 : (double) addToCart / totalEvents);
        return scores;
    }

    private List<String> detectPatterns(List<BehaviorEvent> events, Map<String, Double> scores) {
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

    private boolean isEveningShopper(List<BehaviorEvent> events) {
        long eveningEvents = events.stream()
            .filter(e -> e.getTimestamp() != null)
            .filter(e -> {
                int hour = e.getTimestamp().getHour();
                return hour >= 18 && hour <= 22;
            })
            .count();
        return !events.isEmpty() && (double) eveningEvents / events.size() >= 0.6;
    }

    private boolean isWeekendHeavy(List<BehaviorEvent> events) {
        long weekendEvents = events.stream()
            .filter(e -> e.getTimestamp() != null)
            .filter(e -> {
                switch (e.getTimestamp().getDayOfWeek()) {
                    case SATURDAY, SUNDAY -> {
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            })
            .count();
        return !events.isEmpty() && (double) weekendEvents / events.size() >= 0.5;
    }

    private boolean hasHighSearchIntent(List<BehaviorEvent> events) {
        long searchEvents = events.stream()
            .filter(e -> e.getEventType() == EventType.SEARCH)
            .count();
        return !events.isEmpty() && (double) searchEvents / events.size() >= 0.25;
    }

    private Map<String, Object> detectPreferences(List<BehaviorEvent> events) {
        Map<String, Long> categoryCounts = events.stream()
            .map(event -> event.metadataValue("category").orElse(null))
            .filter(category -> category != null && !category.isBlank())
            .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));

        List<String> topCategories = categoryCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();

        double avgPrice = events.stream()
            .map(event -> event.metadataValue("price").orElse(null))
            .filter(value -> value != null && !value.isBlank())
            .mapToDouble(value -> {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    return 0.0;
                }
            })
            .filter(price -> price > 0)
            .average()
            .orElse(0.0);

        String priceRange;
        if (avgPrice >= 1000) {
            priceRange = "luxury";
        } else if (avgPrice >= 500) {
            priceRange = "premium";
        } else if (avgPrice >= 100) {
            priceRange = "mid_range";
        } else {
            priceRange = "entry";
        }

        return Map.of(
            "preferred_categories", topCategories,
            "price_range", priceRange,
            "search_intensity", events.stream().filter(e -> e.getEventType() == EventType.SEARCH).count()
        );
    }

    private List<String> buildRecommendations(List<String> patterns, Map<String, Double> scores, Map<String, Object> preferences) {
        List<String> recommendations = new ArrayList<>();
        if (patterns.contains("cart_abandoner")) {
            recommendations.add("enable_cart_rescue_sequence");
        }
        if (patterns.contains("frequent_buyer")) {
            recommendations.add("invite_loyalty_program");
        }
        if (patterns.contains("at_risk")) {
            recommendations.add("trigger_win_back_campaign");
        }
        if (scores.getOrDefault("engagement_score", 0.0) > 0.7) {
            recommendations.add("offer_referral_program");
        }
        if (!preferences.isEmpty()) {
            recommendations.add("personalize_catalog_for_" + ((List<?>) preferences.getOrDefault("preferred_categories", List.of("top_category"))).stream().findFirst().orElse("top_category"));
        }
        if (recommendations.isEmpty()) {
            recommendations.add("monitor_behavior");
        }
        return recommendations;
    }

    private String determineSegment(List<String> patterns, Map<String, Double> scores) {
        if (patterns.contains("frequent_buyer") && scores.getOrDefault("engagement_score", 0.0) >= 0.6) {
            return "VIP";
        }
        if (patterns.contains("at_risk") || scores.getOrDefault("churn_risk", 0.0) > 0.7) {
            return "at_risk";
        }
        if (scores.getOrDefault("engagement_score", 0.0) < 0.2) {
            return "dormant";
        }
        return "active";
    }
}
