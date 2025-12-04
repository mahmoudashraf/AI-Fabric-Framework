package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorAlert;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.processing.analyzer.AnomalyAnalyzer;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.storage.BehaviorAlertRepository;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetectionWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorDataProvider dataProvider;
    private final BehaviorAlertRepository alertRepository;
    private final AnomalyAnalyzer anomalyAnalyzer;
    private final BehaviorSchemaRegistry schemaRegistry;

    @Scheduled(cron = "${ai.behavior.processing.anomaly.schedule:0 * * * * *}")
    @Transactional
    public void detect() {
        if (!properties.getProcessing().getAnomaly().isEnabled()) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(1);
        List<String> monitoredSchemas = schemaRegistry.getAll().stream()
            .filter(def -> def.hasTag("transaction"))
            .map(def -> def.getId())
            .toList();
        if (monitoredSchemas.isEmpty()) {
            return;
        }
        List<BehaviorSignal> signals = new ArrayList<>();
        for (String schemaId : monitoredSchemas) {
            signals.addAll(dataProvider.query(BehaviorQuery.builder()
                .schemaId(schemaId)
                .startTime(since)
                .limit(200)
                .build()));
        }
        if (signals.isEmpty()) {
            return;
        }
        double sensitivity = properties.getProcessing().getAnomaly().getSensitivity();
        List<BehaviorAlert> alerts = anomalyAnalyzer.detect(signals, sensitivity);
        if (!alerts.isEmpty()) {
            alertRepository.saveAll(alerts);
            log.debug("Persisted {} behavior alerts", alerts.size());
        }
    }
}
