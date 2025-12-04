package com.ai.behavior.metrics;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.ingestion.event.BehaviorSignalIngested;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricProjectionWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorSchemaRegistry schemaRegistry;
    private final BehaviorMetricsRepository metricsRepository;
    private final List<BehaviorMetricProjector> projectors;

    @Async("behaviorAsyncExecutor")
    @EventListener
    public void onSignal(BehaviorSignalIngested ingested) {
        if (!properties.getProcessing().getAggregation().isEnabled()) {
            return;
        }
        BehaviorSignal signal = ingested.event();
        if (signal.getUserId() == null || signal.getTimestamp() == null) {
            return;
        }
        BehaviorSignalDefinition definition = schemaRegistry.find(signal.getSchemaId()).orElse(null);
        if (definition == null) {
            return;
        }

        List<BehaviorMetricProjector> active = selectProjectors(signal, definition);
        if (active.isEmpty()) {
            return;
        }

        LocalDate metricDate = signal.getTimestamp().toLocalDate();
        BehaviorMetrics metrics = metricsRepository.findByUserIdAndMetricDate(signal.getUserId(), metricDate)
            .orElseGet(() -> BehaviorMetrics.builder()
                .userId(signal.getUserId())
                .tenantId(signal.getTenantId())
                .metricDate(metricDate)
                .build());

        MetricAccumulator accumulator = new MetricAccumulator(metrics);
        for (BehaviorMetricProjector projector : active) {
            try {
                projector.project(signal, definition, accumulator);
            } catch (Exception ex) {
                log.warn("Metric projector {} failed for signal {}", projector.getName(), signal.getId(), ex);
            }
        }

        metrics.setUpdatedAt(LocalDateTime.now());
        metricsRepository.save(metrics);
    }

    private List<BehaviorMetricProjector> selectProjectors(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        if (CollectionUtils.isEmpty(projectors)) {
            return List.of();
        }
        Set<String> enabled = getEnabledProjectorNames();
        return projectors.stream()
            .filter(projector -> enabled.isEmpty() || enabled.contains(projector.getName()))
            .filter(projector -> projector.supports(signal, definition))
            .collect(Collectors.toList());
    }

    private Set<String> getEnabledProjectorNames() {
        List<String> configured = properties.getProcessing().getMetrics().getEnabledProjectors();
        if (CollectionUtils.isEmpty(configured)) {
            return Set.of();
        }
        return configured.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .collect(Collectors.toSet());
    }
}
