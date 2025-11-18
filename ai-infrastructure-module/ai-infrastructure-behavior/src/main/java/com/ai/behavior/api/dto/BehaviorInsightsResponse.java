package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorInsights;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorInsightsResponse {
    @Schema(description = "Unique identifier of the persisted insight snapshot.",
        example = "7d4a6d59-7875-4f7d-8f33-6d88c9bf2a0e")
    UUID id;

    @Schema(description = "User identifier the insights belong to.",
        example = "2ef3fa16-1d12-4c53-9ae5-7dc59a9d24c5")
    UUID userId;

    @Schema(description = "Detected behavior patterns for the user.")
    List<String> patterns;

    @Schema(description = "Raw KPI map for advanced consumers.")
    Map<String, Double> scores;

    @Schema(description = "Quick neutral KPI projection (0-1 scale).")
    NeutralKpiResponse neutralKpis;

    @Schema(description = "Current user segment derived from KPIs.")
    String segment;

    @Schema(description = "Preference metadata captured during analysis.")
    Map<String, Object> preferences;

    @Schema(description = "Recommended follow-up actions.")
    List<String> recommendations;

    @Schema(description = "Timestamp when these insights were generated.")
    LocalDateTime analyzedAt;

    @Schema(description = "Timestamp when these insights should be refreshed.")
    LocalDateTime validUntil;

    @Schema(description = "Version of the analysis strategy.")
    String analysisVersion;

    public static BehaviorInsightsResponse from(BehaviorInsights insights) {
        Map<String, Double> safeScores = insights.getScores() == null
            ? Map.of()
            : Map.copyOf(insights.getScores());
        return BehaviorInsightsResponse.builder()
            .id(insights.getId())
            .userId(insights.getUserId())
            .patterns(insights.getPatterns())
            .scores(safeScores)
            .neutralKpis(NeutralKpiResponse.fromScores(safeScores))
            .segment(insights.getSegment())
            .preferences(insights.getPreferences())
            .recommendations(insights.getRecommendations())
            .analyzedAt(insights.getAnalyzedAt())
            .validUntil(insights.getValidUntil())
            .analysisVersion(insights.getAnalysisVersion())
            .build();
    }
}
