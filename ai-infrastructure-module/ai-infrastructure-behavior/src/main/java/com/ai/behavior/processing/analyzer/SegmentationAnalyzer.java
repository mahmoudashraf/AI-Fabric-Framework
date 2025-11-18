package com.ai.behavior.processing.analyzer;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SegmentationAnalyzer {

    public record SegmentationSnapshot(String segment, Map<String, Object> preferences, List<String> recommendations) {
    }

    public SegmentationSnapshot fromEvents(List<BehaviorSignal> events,
                                           Map<String, Double> scores,
                                           List<String> patterns) {
        Map<String, Object> preferences = detectPreferences(events);
        List<String> recommendations = buildRecommendations(patterns, scores);
        String segment = determineSegment(scores);
        return new SegmentationSnapshot(segment, preferences, recommendations);
    }

    public SegmentationSnapshot fromMetrics(List<BehaviorMetrics> metrics,
                                            BehaviorModuleProperties.Processing.Segmentation config) {
        if (CollectionUtils.isEmpty(metrics)) {
            return new SegmentationSnapshot("insufficient_data", Map.of(), List.of("collect_additional_signals"));
        }

        double totalSignals = metrics.stream().mapToDouble(m -> m.metricValue("count.total")).sum();
        double transactionCount = metrics.stream().mapToDouble(m -> m.metricValue("value.transaction_count")).sum();
        double amountTotal = metrics.stream().mapToDouble(m -> m.metricValue("value.amount_total")).sum();

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("recent_days", metrics.stream()
            .map(BehaviorMetrics::getMetricDate)
            .max(Comparator.naturalOrder())
            .orElse(LocalDate.now()).toString());
        preferences.put("total_signals", totalSignals);
        preferences.put("transaction_count", transactionCount);
        preferences.put("amount_total", amountTotal);

        String segment;
        if (transactionCount > 0 && amountTotal >= config.getVipPurchaseThreshold()) {
            segment = "high_value";
        } else if (totalSignals >= config.getMinEvents()) {
            segment = "active";
        } else if (totalSignals == 0) {
            segment = "new";
        } else {
            segment = "dormant";
        }

        List<String> recommendations = new ArrayList<>();
        recommendations.add(switch (segment) {
            case "high_value" -> "invite_loyalty_program";
            case "active" -> "offer_referral_program";
            case "dormant" -> "trigger_reengagement_sequence";
            default -> "collect_additional_signals";
        });

        return new SegmentationSnapshot(segment, preferences, recommendations);
    }

    private Map<String, Object> detectPreferences(List<BehaviorSignal> events) {
        Map<String, Long> schemaCounts = events.stream()
            .map(BehaviorSignal::getSchemaId)
            .filter(schema -> schema != null && !schema.isBlank())
            .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));

        List<String> topSchemas = schemaCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .toList();

        double avgDuration = events.stream()
            .map(signal -> signal.attributeValue("durationSeconds").orElse(null))
            .filter(value -> value != null && !value.isBlank())
            .mapToDouble(value -> {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    return 0.0;
                }
            })
            .filter(duration -> duration > 0)
            .average()
            .orElse(0.0);

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("top_schemas", topSchemas);
        preferences.put("avg_duration_seconds", avgDuration);
        preferences.put("unique_schemas", schemaCounts.size());
        return preferences;
    }

    private List<String> buildRecommendations(List<String> patterns,
                                              Map<String, Double> scores) {
        List<String> recommendations = new ArrayList<>();
        double recency = scores.getOrDefault("recency_score", 0.0);
        double engagement = scores.getOrDefault("engagement_score", 0.0);
        double diversity = scores.getOrDefault("diversity_score", 0.0);

        if (patterns.contains("dormant") || recency < 0.3) {
            recommendations.add("trigger_reengagement_sequence");
        }
        if (engagement > 0.7) {
            recommendations.add("offer_advocacy_program");
        }
        if (diversity >= 0.6) {
            recommendations.add("promote_cross_sell");
        } else if (diversity < 0.3) {
            recommendations.add("deliver_personalized_bundle");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("monitor_behavior");
        }
        return recommendations;
    }

    private String determineSegment(Map<String, Double> scores) {
        double engagement = scores.getOrDefault("engagement_score", 0.0);
        double recency = scores.getOrDefault("recency_score", 0.0);
        double diversity = scores.getOrDefault("diversity_score", 0.0);
        if (engagement >= 0.75 && recency >= 0.6 && diversity >= 0.5) {
            return "active_explorer";
        }
        if (engagement >= 0.75 && recency >= 0.6) {
            return "active_specialist";
        }
        if (engagement >= 0.4 && recency >= 0.4) {
            return diversity >= 0.4 ? "steady" : "steady_focused";
        }
        if (recency < 0.2) {
            return "dormant";
        }
        if (diversity < 0.3) {
            return "focused";
        }
        return "emerging";
    }
}
