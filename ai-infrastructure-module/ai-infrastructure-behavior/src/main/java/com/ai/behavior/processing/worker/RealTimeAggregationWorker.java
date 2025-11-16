package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.ingestion.event.BehaviorEventIngested;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealTimeAggregationWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorMetricsRepository metricsRepository;

    @Async("behaviorAsyncExecutor")
    @EventListener
    public void onEvent(BehaviorEventIngested ingested) {
        if (!properties.getProcessing().getAggregation().isEnabled()) {
            return;
        }
        BehaviorEvent event = ingested.event();
        if (event.getUserId() == null || event.getTimestamp() == null) {
            return;
        }
        LocalDate metricDate = event.getTimestamp().toLocalDate();
        BehaviorMetrics metrics = metricsRepository.findByUserIdAndMetricDate(event.getUserId(), metricDate)
            .orElseGet(() -> BehaviorMetrics.builder()
                .userId(event.getUserId())
                .metricDate(metricDate)
                .build());

        switch (event.getEventType()) {
            case VIEW -> metrics.incrementView();
            case CLICK -> metrics.incrementClick();
            case SEARCH -> metrics.incrementSearch();
            case ADD_TO_CART -> metrics.incrementAddToCart();
            case PURCHASE -> {
                double amount = event.metadataValue("amount").map(value -> {
                    try {
                        return Double.parseDouble(value);
                    } catch (NumberFormatException ex) {
                        return 0.0;
                    }
                }).orElse(0.0);
                metrics.incrementPurchase(amount);
            }
            case FEEDBACK, REVIEW -> metrics.incrementFeedback();
            default -> {
                // ignore
            }
        }

        metricsRepository.save(metrics);
    }
}
