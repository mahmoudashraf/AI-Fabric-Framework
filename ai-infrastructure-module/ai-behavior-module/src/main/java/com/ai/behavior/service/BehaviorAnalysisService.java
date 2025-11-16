package com.ai.behavior.service;

import com.ai.behavior.analytics.BehaviorAnalyzer;
import com.ai.behavior.config.BehaviorProperties;
import com.ai.behavior.exception.BehaviorAnalysisException;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {

    private final List<BehaviorAnalyzer> analyzers;
    private final BehaviorDataProvider dataProvider;
    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorProperties properties;
    private final Clock clock;

    @Transactional
    public BehaviorInsights analyze(UUID userId) {
        List<BehaviorEvent> events = dataProvider.getRecentEvents(userId, 1000);
        return analyze(userId, events);
    }

    @Transactional
    public BehaviorInsights analyze(UUID userId, List<BehaviorEvent> events) {
        if (events.size() < properties.getInsights().getMinEventsForInsights()) {
            throw new BehaviorAnalysisException("Not enough events to analyze behavior for user " + userId);
        }

        List<BehaviorInsights> partials = new ArrayList<>();
        for (BehaviorAnalyzer analyzer : analyzers) {
            if (analyzer.supports(events)) {
                try {
                    partials.add(analyzer.analyze(userId, events));
                } catch (Exception ex) {
                    log.warn("Behavior analyzer {} failed: {}", analyzer.getAnalyzerType(), ex.getMessage());
                }
            }
        }

        BehaviorInsights merged = mergePartials(userId, partials);
        return insightsRepository.save(merged);
    }

    private BehaviorInsights mergePartials(UUID userId, List<BehaviorInsights> partials) {
        if (partials.isEmpty()) {
            LocalDateTime now = LocalDateTime.now(clock);
            return BehaviorInsights.builder()
                .userId(userId)
                .patterns(List.of("insufficient_data"))
                .scores(Map.of("engagement_score", 0.0))
                .segment("active")
                .preferences(Map.of())
                .recommendations(List.of("Collect more behavioral signals"))
                .analyzedAt(now)
                .analysisVersion("composite-1.0")
                .validUntil(now.plus(properties.getInsights().getValidity()))
                .build();
        }
        List<String> patterns = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();
        Map<String, Object> preferences = new HashMap<>();
        List<String> recommendations = new ArrayList<>();

        for (BehaviorInsights partial : partials) {
            if (partial.getPatterns() != null) {
                partial.getPatterns().forEach(pattern -> {
                    if (!patterns.contains(pattern)) {
                        patterns.add(pattern);
                    }
                });
            }
            if (partial.getScores() != null) {
                partial.getScores().forEach(scores::putIfAbsent);
            }
            if (partial.getPreferences() != null) {
                partial.getPreferences().forEach(preferences::putIfAbsent);
            }
            if (partial.getRecommendations() != null) {
                partial.getRecommendations().forEach(recommendations::add);
            }
        }

        String segment = partials.stream()
            .map(BehaviorInsights::getSegment)
            .filter(s -> s != null && !s.isBlank())
            .findFirst()
            .orElse("active");

        LocalDateTime now = LocalDateTime.now(clock);
        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(patterns)
            .scores(scores)
            .segment(segment)
            .preferences(preferences)
            .recommendations(recommendations)
            .analyzedAt(now)
            .analysisVersion("composite-1.0")
            .validUntil(now.plus(properties.getInsights().getValidity()))
            .build();
    }
}
