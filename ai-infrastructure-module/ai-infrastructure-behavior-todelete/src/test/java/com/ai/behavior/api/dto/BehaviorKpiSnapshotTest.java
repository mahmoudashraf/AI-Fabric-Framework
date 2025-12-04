package com.ai.behavior.api.dto;

import com.ai.behavior.metrics.KpiKeys;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorKpiSnapshotTest {

    @Test
    void derivesSnapshotFromMetricMap() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("count.total", 20.0);
        metrics.put(KpiKeys.ENGAGEMENT_SCORE, 0.75);
        metrics.put(KpiKeys.RECENCY_SCORE, 0.5);
        metrics.put(KpiKeys.RECENCY_HOURS_SINCE_LAST, 12.0);
        metrics.put("schema.engagement.view", 10.0);
        metrics.put("schema.intent.search", 5.0);

        BehaviorKpiSnapshot snapshot = BehaviorKpiSnapshot.from(metrics);

        assertThat(snapshot.getEngagementScore()).isEqualTo(0.75);
        assertThat(snapshot.getRecencyScore()).isEqualTo(0.5);
        assertThat(snapshot.getUniqueSchemaCount()).isEqualTo(2);
        assertThat(snapshot.getDiversityScore()).isBetween(0.0, 1.0);
        assertThat(snapshot.getHoursSinceLastSignal()).isEqualTo(12.0);
    }
}
