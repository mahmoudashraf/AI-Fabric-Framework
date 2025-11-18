package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorMetrics;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorMetricsResponseTest {

    @Test
    void neutralKpisAreDerivedFromKpiKeys() {
        BehaviorMetrics metrics = BehaviorMetrics.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .metricDate(LocalDate.now())
            .metrics(Map.of(
                "kpi.engagement_score", 0.9d,
                "kpi.recency_score", 0.5d,
                "kpi.diversity_score", 0.7d
            ))
            .attributes(Map.of("diversity.schemas", List.of("engagement.view")))
            .build();

        BehaviorMetricsResponse response = BehaviorMetricsResponse.from(metrics);

        assertThat(response.getNeutralKpis().getEngagement()).isEqualTo(0.9d);
        assertThat(response.getNeutralKpis().getRecency()).isEqualTo(0.5d);
        assertThat(response.getNeutralKpis().getDiversity()).isEqualTo(0.7d);
        assertThat(response.getAttributes()).containsKey("diversity.schemas");
    }
}
