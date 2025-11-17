package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.processing.worker.UserSegmentationWorker;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import com.ai.infrastructure.it.TestApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class UserSegmentationWorkerIntegrationTest {

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

        for (int day = 0; day < 20; day++) {
            behaviorMetricsRepository.save(BehaviorMetrics.builder()
                .userId(userId)
                .metricDate(LocalDate.now().minusDays(day))
                .viewCount(40 + day)
                .clickCount(10 + day)
                .addToCartCount(5 + (day % 3))
                .purchaseCount(day % 4 == 0 ? 2 : 0)
                .totalRevenue(1200 + (day * 50))
                .conversionRate(0.2)
                .build());
        }

        behaviorInsightsRepository.save(BehaviorInsights.builder()
            .userId(userId)
            .patterns(List.of("insufficient_data"))
            .scores(Map.of("engagement_score", 0.1))
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

        assertThat(refreshed.getSegment()).isIn("VIP", "active");
        assertThat(refreshed.getPreferences()).isNotEmpty();
        assertThat(refreshed.getRecommendations()).isNotEmpty();
        assertThat(refreshed.getAnalyzedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }
}
