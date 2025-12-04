package com.ai.behavior.dto;

import com.ai.behavior.model.BehaviorInsights;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BehaviorInsightView(
    UUID userId,
    String segment,
    List<String> patterns,
    List<String> recommendations,
    Map<String, Double> scores,
    LocalDateTime analyzedAt
) {

    public static BehaviorInsightView from(BehaviorInsights insights) {
        return new BehaviorInsightView(
            insights.getUserId(),
            insights.getSegment(),
            insights.getPatterns(),
            insights.getRecommendations(),
            insights.getScores(),
            insights.getAnalyzedAt()
        );
    }
}
