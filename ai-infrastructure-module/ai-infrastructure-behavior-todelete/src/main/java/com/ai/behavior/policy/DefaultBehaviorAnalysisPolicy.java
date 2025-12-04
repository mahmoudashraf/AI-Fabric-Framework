package com.ai.behavior.policy;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnMissingBean(BehaviorAnalysisPolicy.class)
@RequiredArgsConstructor
public class DefaultBehaviorAnalysisPolicy implements BehaviorAnalysisPolicy {

    private final BehaviorModuleProperties properties;

    @Override
    public List<String> detectPatterns(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
        if (CollectionUtils.isEmpty(events)) {
            return List.of("no_activity");
        }
        Map<String, Long> counts = events.stream()
            .filter(event -> event.getEventType() != null)
            .collect(Collectors.groupingBy(BehaviorEventEntity::getEventType, Collectors.counting()));

        List<String> patterns = new ArrayList<>();
        double engagementScore = scores.getOrDefault("engagementScore", 0.0);
        double recencyScore = scores.getOrDefault("recencyScore", 0.0);

        if (engagementScore >= properties.getProcessing().getAnalyzer().getEngagementThreshold()) {
            patterns.add("high_engagement");
        } else if (engagementScore >= 0.5) {
            patterns.add("consistent_activity");
        } else {
            patterns.add("low_engagement");
        }

        if (recencyScore >= properties.getProcessing().getAnalyzer().getRecencyThreshold()) {
            patterns.add("recent_activity");
        } else {
            patterns.add("stale_activity");
        }

        if (counts.entrySet().stream().anyMatch(entry -> entry.getKey().contains("purchase"))) {
            patterns.add("commerce_intent");
        }
        if (counts.entrySet().stream().anyMatch(entry -> entry.getKey().contains("support"))) {
            patterns.add("support_intent");
        }
        if (counts.entrySet().stream().anyMatch(entry -> entry.getKey().contains("search"))) {
            patterns.add("discovery_intent");
        }

        return patterns.stream().distinct().toList();
    }

    @Override
    public String determineSegment(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
        double engagementScore = scores.getOrDefault("engagementScore", 0.0);
        double recencyScore = scores.getOrDefault("recencyScore", 0.0);

        if (engagementScore >= 0.85 && recencyScore >= 0.8) {
            return "power_user";
        }
        if (engagementScore >= 0.65 && recencyScore >= 0.6) {
            return "active";
        }
        if (engagementScore >= 0.4) {
            return "steady";
        }
        return recencyScore < 0.3 ? "dormant" : "emerging";
    }

    @Override
    public List<String> generateRecommendations(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
        List<String> recommendations = new ArrayList<>();
        double engagementScore = scores.getOrDefault("engagementScore", 0.0);
        double recencyScore = scores.getOrDefault("recencyScore", 0.0);

        if (engagementScore >= 0.8) {
            recommendations.add("offer_loyalty_reward");
            recommendations.add("invite_beta_program");
        } else if (engagementScore >= 0.5) {
            recommendations.add("send_personalized_recommendations");
        } else {
            recommendations.add("trigger_reengagement_sequence");
        }

        if (recencyScore < 0.4) {
            recommendations.add("send_recency_nudge");
        }

        Map<String, Long> typeCounts = events.stream()
            .filter(event -> event.getEventType() != null)
            .collect(Collectors.groupingBy(BehaviorEventEntity::getEventType, Collectors.counting()));

        typeCounts.entrySet().stream()
            .max(Comparator.comparingLong(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .ifPresent(topEvent -> recommendations.add("highlight_similar_" + sanitize(topEvent)));

        return recommendations.stream().distinct().toList();
    }

    @Override
    public double calculateConfidence(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores) {
        if (CollectionUtils.isEmpty(events)) {
            return 0.2;
        }
        double engagementScore = scores.getOrDefault("engagementScore", 0.0);
        double recencyScore = scores.getOrDefault("recencyScore", 0.0);

        long uniqueDays = events.stream()
            .map(BehaviorEventEntity::getCreatedAt)
            .filter(Objects::nonNull)
            .map(OffsetDateTime::toLocalDate)
            .distinct()
            .count();

        double coverageScore = Math.min(1.0, uniqueDays / 30.0);
        double base = (engagementScore + recencyScore + coverageScore) / 3.0;
        return Math.min(1.0, Math.max(0.15, base));
    }

    private String sanitize(String input) {
        return input == null ? "event" : input.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }
}
