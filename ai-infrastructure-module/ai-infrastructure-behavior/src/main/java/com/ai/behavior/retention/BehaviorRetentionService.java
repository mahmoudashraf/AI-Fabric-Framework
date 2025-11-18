package com.ai.behavior.retention;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.storage.BehaviorAlertRepository;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.behavior.storage.BehaviorSignalRepository;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorRetentionService {

    private final BehaviorModuleProperties properties;
    private final BehaviorSignalRepository eventRepository;
    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorEmbeddingRepository embeddingRepository;
    private final BehaviorAlertRepository alertRepository;

    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void enforceRetention() {
        var retention = properties.getRetention();
        long deletedEvents = eventRepository.deleteByTimestampBefore(LocalDateTime.now().minusDays(retention.getEventsDays()));
        long deletedInsights = insightsRepository.deleteByValidUntilBefore(LocalDateTime.now().minusDays(retention.getInsightsDays()));
        long deletedMetrics = metricsRepository.deleteByMetricDateBefore(LocalDate.now().minusDays(retention.getMetricsDays()));
        long deletedEmbeddings = embeddingRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(retention.getEmbeddingsDays()));
        long deletedAlerts = alertRepository.deleteByDetectedAtBefore(LocalDateTime.now().minusDays(retention.getAlertsDays()));
        if (deletedEvents + deletedInsights + deletedMetrics + deletedEmbeddings + deletedAlerts > 0) {
            log.info("Behavior retention cleanup removed {} events, {} insights, {} metrics, {} embeddings, {} alerts",
                deletedEvents, deletedInsights, deletedMetrics, deletedEmbeddings, deletedAlerts);
        }
    }
}
