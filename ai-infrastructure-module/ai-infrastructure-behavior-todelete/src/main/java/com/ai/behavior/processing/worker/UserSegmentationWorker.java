package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.processing.analyzer.SegmentationAnalyzer;
import com.ai.behavior.processing.analyzer.SegmentationAnalyzer.SegmentationSnapshot;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSegmentationWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorInsightsRepository insightsRepository;
    private final SegmentationAnalyzer segmentationAnalyzer;

    @Scheduled(cron = "${ai.behavior.processing.segmentation.schedule:0 30 2 * * *}")
    @Transactional
    public void refreshSegments() {
        var config = properties.getProcessing().getSegmentation();
        if (!config.isEnabled()) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(config.getAnalysisWindowDays());
        List<BehaviorMetrics> recentMetrics = metricsRepository.findByMetricDateAfter(cutoff);
        if (recentMetrics.isEmpty()) {
            return;
        }
        Map<UUID, List<BehaviorMetrics>> metricsByUser = recentMetrics.stream()
            .collect(Collectors.groupingBy(BehaviorMetrics::getUserId));

        metricsByUser.forEach((userId, userMetrics) -> {
            if (userMetrics.size() < config.getMinEvents()) {
                return;
            }
            SegmentationSnapshot snapshot = segmentationAnalyzer.fromMetrics(userMetrics, config);
            insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId).ifPresent(insights -> {
                boolean changed = !Objects.equals(insights.getSegment(), snapshot.segment()) ||
                    !Objects.equals(insights.getPreferences(), snapshot.preferences());
                if (changed) {
                    insights.setSegment(snapshot.segment());
                    insights.setPreferences(snapshot.preferences());
                    insights.setRecommendations(snapshot.recommendations());
                    insights.setAnalyzedAt(LocalDateTime.now());
                    insightsRepository.save(insights);
                }
            });
        });
        log.debug("UserSegmentationWorker processed {} users", metricsByUser.size());
    }
}
