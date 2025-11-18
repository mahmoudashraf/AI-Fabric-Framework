package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorAlert;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import com.ai.behavior.processing.analyzer.AnomalyAnalyzer;
import com.ai.behavior.storage.BehaviorAlertRepository;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetectionWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorDataProvider dataProvider;
    private final BehaviorAlertRepository alertRepository;
    private final AnomalyAnalyzer anomalyAnalyzer;

    @Scheduled(cron = "${ai.behavior.processing.anomaly.schedule:0 * * * * *}")
    @Transactional
    public void detect() {
        if (!properties.getProcessing().getAnomaly().isEnabled()) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(1);
        List<BehaviorSignal> purchases = dataProvider.query(BehaviorQuery.builder()
            .eventType(EventType.PURCHASE)
            .startTime(since)
            .limit(500)
            .build());
        if (purchases.isEmpty()) {
            return;
        }
        double sensitivity = properties.getProcessing().getAnomaly().getSensitivity();
        List<BehaviorAlert> alerts = anomalyAnalyzer.detect(purchases, sensitivity);
        if (!alerts.isEmpty()) {
            alertRepository.saveAll(alerts);
            log.debug("Persisted {} behavior alerts", alerts.size());
        }
    }
}
