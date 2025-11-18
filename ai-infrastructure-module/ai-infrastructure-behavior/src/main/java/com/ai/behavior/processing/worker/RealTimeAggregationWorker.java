package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.ingestion.event.BehaviorSignalIngested;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealTimeAggregationWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorSchemaRegistry schemaRegistry;

    @Async("behaviorAsyncExecutor")
    @EventListener
    public void onEvent(BehaviorSignalIngested ingested) {
        if (!properties.getProcessing().getAggregation().isEnabled()) {
            return;
        }
        BehaviorSignal event = ingested.event();
        if (event.getUserId() == null || event.getTimestamp() == null) {
            return;
        }
        LocalDate metricDate = event.getTimestamp().toLocalDate();
        BehaviorMetrics metrics = metricsRepository.findByUserIdAndMetricDate(event.getUserId(), metricDate)
            .orElseGet(() -> BehaviorMetrics.builder()
                .userId(event.getUserId())
                .tenantId(event.getTenantId())
                .metricDate(metricDate)
                .build());

        metrics.incrementMetric("count.total", 1.0d);
        metrics.incrementMetric("count.schema." + sanitize(event.getSchemaId()), 1.0d);

        schemaRegistry.find(event.getSchemaId()).ifPresent(definition ->
            definition.getTags().forEach(tag ->
                metrics.incrementMetric("count.tag." + sanitize(tag), 1.0d)
            )
        );

        event.attributeValue("durationSeconds")
            .map(this::safeDouble)
            .ifPresent(duration -> metrics.incrementMetric("duration.total_seconds", duration));

        event.attributeValue("amount")
            .map(this::safeDouble)
            .ifPresent(amount -> {
                metrics.incrementMetric("value.amount_total", amount);
                metrics.incrementMetric("value.transaction_count", 1.0d);
            });

        metrics.setUpdatedAt(LocalDateTime.now());
        metricsRepository.save(metrics);
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private double safeDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }
}
