package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorInsights;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorInsightsResponse {
    UUID id;
    UUID userId;
    List<String> patterns;
    String segment;
    Map<String, Object> preferences;
    List<String> recommendations;
    LocalDateTime analyzedAt;
    LocalDateTime validUntil;
    String analysisVersion;
    BehaviorKpiSnapshot kpis;
    Map<String, Double> scores;

    public static BehaviorInsightsResponse from(BehaviorInsights insights) {
        Map<String, Double> safeScores = insights.safeScores();
        return BehaviorInsightsResponse.builder()
            .id(insights.getId())
            .userId(insights.getUserId())
            .patterns(insights.getPatterns())
            .segment(insights.getSegment())
            .preferences(insights.getPreferences())
            .recommendations(insights.getRecommendations())
            .analyzedAt(insights.getAnalyzedAt())
            .validUntil(insights.getValidUntil())
            .analysisVersion(insights.getAnalysisVersion())
            .kpis(BehaviorKpiSnapshot.from(safeScores))
            .scores(safeScores)
            .build();
    }
}
