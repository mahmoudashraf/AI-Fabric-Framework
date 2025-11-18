package com.ai.behavior.processing.analyzer;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorSignal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatternAnalyzerTest {

    private final BehaviorModuleProperties properties = new BehaviorModuleProperties();
    private final SegmentationAnalyzer segmentationAnalyzer = new SegmentationAnalyzer();
    private final PatternAnalyzer analyzer = new PatternAnalyzer(properties, segmentationAnalyzer);

    @Test
    void analyzeProducesNeutralKpis() {
        UUID userId = UUID.randomUUID();
        List<BehaviorSignal> events = List.of(
            signal(userId, "engagement.view", 1),
            signal(userId, "intent.search", 2),
            signal(userId, "conversion.transaction", 5)
        );

        BehaviorInsights insights = analyzer.analyze(userId, events);

        assertThat(insights.getScores())
            .containsKeys("engagement_score", "recency_score", "diversity_score");
        assertThat(insights.getPatterns()).isNotEmpty();
        assertThat(insights.getSegment()).isNotBlank();
    }

    private BehaviorSignal signal(UUID userId, String schemaId, int hoursAgo) {
        return BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .schemaId(schemaId)
            .timestamp(LocalDateTime.now().minusHours(hoursAgo))
            .build();
    }
}
