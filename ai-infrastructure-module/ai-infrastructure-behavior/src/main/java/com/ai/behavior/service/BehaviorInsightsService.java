package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorInsightsService {

    private static final List<String> REQUIRED_KPI_KEYS = List.of(
        "engagement_score",
        "recency_score",
        "diversity_score"
    );

    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorAnalysisService analysisService;

    @Transactional(readOnly = true)
    public BehaviorInsights getUserInsights(UUID userId) {
        return insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId)
            .filter(BehaviorInsights::isValid)
            .map(this::ensureNeutralKpis)
            .orElseGet(() -> refreshInsights(userId));
    }

    @Transactional
    public BehaviorInsights refreshInsights(UUID userId) {
        log.debug("Refreshing behavior insights for user {}", userId);
        BehaviorInsights insights = ensureNeutralKpis(analysisService.analyze(userId));
        return insightsRepository.save(insights);
    }

    private BehaviorInsights ensureNeutralKpis(BehaviorInsights insights) {
        if (insights == null) {
            return null;
        }
        Map<String, Double> normalized = new HashMap<>();
        if (insights.getScores() != null) {
            normalized.putAll(insights.getScores());
        }
        REQUIRED_KPI_KEYS.forEach(key -> normalized.putIfAbsent(key, 0.0d));
        insights.setScores(normalized);
        return insights;
    }
}
