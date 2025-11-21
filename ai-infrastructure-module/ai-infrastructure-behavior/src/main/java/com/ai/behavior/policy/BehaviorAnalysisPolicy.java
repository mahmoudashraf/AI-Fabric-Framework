package com.ai.behavior.policy;

import com.ai.behavior.model.BehaviorEventEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hook interface that enables customers to plug in domain-specific
 * behavior analysis logic (patterns, segments, recommendations, confidence).
 */
public interface BehaviorAnalysisPolicy {

    List<String> detectPatterns(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores);

    String determineSegment(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores);

    List<String> generateRecommendations(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores);

    double calculateConfidence(UUID userId, List<BehaviorEventEntity> events, Map<String, Double> scores);
}
