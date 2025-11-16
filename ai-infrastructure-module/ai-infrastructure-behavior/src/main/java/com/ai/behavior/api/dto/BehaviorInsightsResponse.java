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
    Map<String, Double> scores;
    String segment;
    Map<String, Object> preferences;
    List<String> recommendations;
    LocalDateTime analyzedAt;
    LocalDateTime validUntil;
    String analysisVersion;

    public static BehaviorInsightsResponse from(BehaviorInsights insights) {
        return BehaviorInsightsResponse.builder()
            .id(insights.getId())
            .userId(insights.getUserId())
            .patterns(insights.getPatterns())
            .scores(insights.getScores())
            .segment(insights.getSegment())
            .preferences(insights.getPreferences())
            .recommendations(insights.getRecommendations())
            .analyzedAt(insights.getAnalyzedAt())
            .validUntil(insights.getValidUntil())
            .analysisVersion(insights.getAnalysisVersion())
            .build();
    }
}
