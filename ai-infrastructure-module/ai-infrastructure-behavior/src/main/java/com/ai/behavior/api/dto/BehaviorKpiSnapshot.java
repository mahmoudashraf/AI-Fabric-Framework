package com.ai.behavior.api.dto;

import com.ai.behavior.metrics.KpiKeys;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class BehaviorKpiSnapshot {
    double engagementScore;
    double recencyScore;
    double diversityScore;
    double interactionVelocity;
    double uniqueSchemaRatio;
    long uniqueSchemaCount;
    double hoursSinceLastSignal;

    public static BehaviorKpiSnapshot from(Map<String, Double> metrics) {
        Map<String, Double> safe = metrics == null ? Collections.emptyMap() : metrics;
        long uniqueSchemas = safe.keySet().stream()
            .filter(key -> key.startsWith("schema."))
            .distinct()
            .count();
        if (safe.containsKey(KpiKeys.DIVERSITY_UNIQUE_SCHEMA_COUNT)) {
            uniqueSchemas = safe.get(KpiKeys.DIVERSITY_UNIQUE_SCHEMA_COUNT).longValue();
        }
        double totalSignals = safe.getOrDefault("count.total", 0.0d);
        double defaultRatio = totalSignals <= 0 ? 0.0d : Math.min(1.0d, uniqueSchemas / Math.max(1.0d, totalSignals));
        double diversityRatio = safe.getOrDefault(KpiKeys.DIVERSITY_UNIQUE_SCHEMA_RATIO, defaultRatio);
        double diversityScore = safe.getOrDefault(KpiKeys.DIVERSITY_SCORE, diversityRatio);

        return BehaviorKpiSnapshot.builder()
            .engagementScore(clamp(safe.getOrDefault(KpiKeys.ENGAGEMENT_SCORE, 0.0d)))
            .recencyScore(clamp(safe.getOrDefault(KpiKeys.RECENCY_SCORE, 0.0d)))
            .diversityScore(clamp(diversityScore))
            .interactionVelocity(clamp(safe.getOrDefault(KpiKeys.ENGAGEMENT_VELOCITY, 0.0d)))
            .uniqueSchemaRatio(clamp(diversityRatio))
            .uniqueSchemaCount(uniqueSchemas)
            .hoursSinceLastSignal(Math.max(0.0d, safe.getOrDefault(KpiKeys.RECENCY_HOURS_SINCE_LAST, 0.0d)))
            .build();
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("engagementScore", engagementScore);
        map.put("recencyScore", recencyScore);
        map.put("diversityScore", diversityScore);
        map.put("interactionVelocity", interactionVelocity);
        map.put("uniqueSchemaRatio", uniqueSchemaRatio);
        map.put("uniqueSchemaCount", uniqueSchemaCount);
        map.put("hoursSinceLastSignal", hoursSinceLastSignal);
        return map;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
