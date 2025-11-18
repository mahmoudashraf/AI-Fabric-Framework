package com.ai.behavior.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Optional;

@Value
@Builder
public class NeutralKpiResponse {

    @Schema(description = "Normalized 0-1 engagement score derived from total interactions, value, and dwell time.",
        example = "0.78")
    double engagement;

    @Schema(description = "Recency score where 1 represents a fresh interaction and 0 indicates stale behavior.",
        example = "0.42")
    double recency;

    @Schema(description = "Diversity score that captures how many distinct schemas/domains the user engages with.",
        example = "0.66")
    double diversity;

    public static NeutralKpiResponse fromScores(Map<String, Double> scores) {
        Map<String, Double> safe = Optional.ofNullable(scores).orElseGet(Map::of);
        return NeutralKpiResponse.builder()
            .engagement(safe.getOrDefault("engagement_score", 0.0d))
            .recency(safe.getOrDefault("recency_score", 0.0d))
            .diversity(safe.getOrDefault("diversity_score", 0.0d))
            .build();
    }

    public static NeutralKpiResponse fromMetrics(Map<String, Double> metrics) {
        Map<String, Double> safe = Optional.ofNullable(metrics).orElseGet(Map::of);
        return NeutralKpiResponse.builder()
            .engagement(safe.getOrDefault("kpi.engagement_score", 0.0d))
            .recency(safe.getOrDefault("kpi.recency_score", 0.0d))
            .diversity(safe.getOrDefault("kpi.diversity_score", 0.0d))
            .build();
    }
}
