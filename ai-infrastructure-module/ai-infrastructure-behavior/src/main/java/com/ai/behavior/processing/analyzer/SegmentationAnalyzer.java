package com.ai.behavior.processing.analyzer;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.model.EventType;
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

    public SegmentationSnapshot fromEvents(List<BehaviorEvent> events,
                                           Map<String, Double> scores,
                                           List<String> patterns) {
        Map<String, Object> preferences = detectPreferences(events);
        List<String> recommendations = buildRecommendations(patterns, scores, preferences);
        String segment = determineSegment(patterns, scores);
        return new SegmentationSnapshot(segment, preferences, recommendations);
    }

    public SegmentationSnapshot fromMetrics(List<BehaviorMetrics> metrics,
                                            BehaviorModuleProperties.Processing.Segmentation config) {
        if (CollectionUtils.isEmpty(metrics)) {
            return new SegmentationSnapshot("insufficient_data", Map.of(), List.of("collect_additional_signals"));
        }

        double totalRevenue = metrics.stream().mapToDouble(BehaviorMetrics::getTotalRevenue).sum();
        int totalViews = metrics.stream().mapToInt(BehaviorMetrics::getViewCount).sum();
        int totalPurchases = metrics.stream().mapToInt(BehaviorMetrics::getPurchaseCount).sum();

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("recent_days", metrics.stream()
            .map(BehaviorMetrics::getMetricDate)
            .max(Comparator.naturalOrder())
            .orElse(LocalDate.now()).toString());
        preferences.put("avg_conversion_rate", totalViews == 0 ? 0.0 : (double) totalPurchases / totalViews);
        preferences.put("total_revenue", totalRevenue);

        String segment;
        if (totalRevenue >= config.getVipPurchaseThreshold()) {
            segment = "VIP";
        } else if (totalPurchases > 0 && totalViews > 30) {
            segment = "active";
        } else if (totalViews > 50 && totalPurchases == 0) {
            segment = "consideration";
        } else if (totalViews < config.getMinEvents()) {
            segment = "new_user";
        } else {
            segment = "dormant";
        }

        List<String> recommendations = new ArrayList<>();
        switch (segment) {
            case "VIP" -> recommendations.add("invite_loyalty_program");
            case "consideration" -> recommendations.add("personalized_concierge_outreach");
            case "dormant" -> recommendations.add("trigger_win_back_campaign");
            case "active" -> recommendations.add("offer_referral_program");
            default -> recommendations.add("monitor_behavior");
        }

        return new SegmentationSnapshot(segment, preferences, recommendations);
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

        Map<String, Object> preferences = new HashMap<>();
        preferences.put("preferred_categories", topCategories);
        preferences.put("price_range", priceRange);
        preferences.put("search_intensity", events.stream().filter(e -> e.getEventType() == EventType.SEARCH).count());
        return preferences;
    }

    private List<String> buildRecommendations(List<String> patterns,
                                              Map<String, Double> scores,
                                              Map<String, Object> preferences) {
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
            Object firstPreference = preferences.getOrDefault("preferred_categories", Collections.emptyList());
            if (firstPreference instanceof List<?> list && !list.isEmpty()) {
                recommendations.add("personalize_catalog_for_" + list.get(0));
            }
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
        if (patterns.contains("cart_abandoner")) {
            return "needs_nurturing";
        }
        return "active";
    }
}
