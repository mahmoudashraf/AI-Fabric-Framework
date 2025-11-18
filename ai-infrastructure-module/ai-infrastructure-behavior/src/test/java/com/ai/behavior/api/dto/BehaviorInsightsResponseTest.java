package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorInsights;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorInsightsResponseTest {

    @Test
    void neutralKpisMirrorScores() {
        BehaviorInsights insights = BehaviorInsights.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .scores(Map.of(
                "engagement_score", 0.65d,
                "recency_score", 0.4d,
                "diversity_score", 0.8d
            ))
            .patterns(java.util.List.of("power_user"))
            .segment("active_explorer")
            .preferences(Map.of())
            .recommendations(java.util.List.of("monitor_behavior"))
            .analyzedAt(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusMinutes(5))
            .analysisVersion("2.0.0")
            .build();

        BehaviorInsightsResponse response = BehaviorInsightsResponse.from(insights);

        assertThat(response.getNeutralKpis().getEngagement()).isEqualTo(0.65d);
        assertThat(response.getNeutralKpis().getRecency()).isEqualTo(0.4d);
        assertThat(response.getNeutralKpis().getDiversity()).isEqualTo(0.8d);
    }
}
