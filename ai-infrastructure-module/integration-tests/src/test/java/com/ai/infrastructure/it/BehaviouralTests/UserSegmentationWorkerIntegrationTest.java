package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.metrics.KpiKeys;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.processing.worker.UserSegmentationWorker;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.config.PostgresTestContainerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@Import(PostgresTestContainerConfig.class)
public class UserSegmentationWorkerIntegrationTest {

    @Autowired
    private UserSegmentationWorker userSegmentationWorker;

    @Autowired
    private BehaviorMetricsRepository behaviorMetricsRepository;

    @Autowired
    private BehaviorInsightsRepository behaviorInsightsRepository;

    @AfterEach
    void clean() {
        behaviorInsightsRepository.deleteAll();
        behaviorMetricsRepository.deleteAll();
    }

    @Test
    @DisplayName("Segmentation worker updates cached insights when metrics meet thresholds")
    void workerRefreshesSegments() {
        UUID userId = UUID.randomUUID();

        for (int day = 0; day < 30; day++) {
            Map<String, Double> dailyMetrics = new HashMap<>();
            dailyMetrics.put("count.total", 40.0 + day);
            dailyMetrics.put("value.transaction_count", day % 4 == 0 ? 2.0 : 0.0);
            dailyMetrics.put("value.amount_total", 1200.0 + (day * 50));
            dailyMetrics.put(KpiKeys.ENGAGEMENT_SCORE, Math.min(1.0, 0.6 + (day * 0.01)));
            dailyMetrics.put(KpiKeys.RECENCY_SCORE, 0.7);
            dailyMetrics.put(KpiKeys.DIVERSITY_SCORE, 0.4 + (day * 0.005));

            behaviorMetricsRepository.save(BehaviorMetrics.builder()
                .userId(userId)
                .metricDate(LocalDate.now().minusDays(day))
                .metrics(dailyMetrics)
                .build());
        }

        behaviorInsightsRepository.save(BehaviorInsights.builder()
            .userId(userId)
            .patterns(List.of("insufficient_data"))
            .scores(Map.of(
                KpiKeys.ENGAGEMENT_SCORE, 0.1,
                KpiKeys.RECENCY_SCORE, 0.1,
                KpiKeys.DIVERSITY_SCORE, 0.1
            ))
            .segment("new_user")
            .preferences(Map.of())
            .recommendations(List.of("collect_additional_signals"))
            .analyzedAt(LocalDateTime.now().minusDays(3))
            .validUntil(LocalDateTime.now().plusDays(3))
            .analysisVersion("0.9")
            .build());

        userSegmentationWorker.refreshSegments();

        BehaviorInsights refreshed = behaviorInsightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId)
            .orElseThrow();

        assertThat(refreshed.getSegment()).isEqualTo("VIP");
        assertThat(refreshed.getPreferences()).isNotEmpty();
        assertThat(refreshed.getRecommendations()).isNotEmpty();
        assertThat(refreshed.getAnalyzedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }
}
