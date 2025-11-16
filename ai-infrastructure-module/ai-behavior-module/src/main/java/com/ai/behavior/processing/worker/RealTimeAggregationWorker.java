package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorProperties;
import com.ai.behavior.ingestion.event.BehaviorEventIngested;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.repository.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealTimeAggregationWorker {

    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorProperties properties;

    @Async("aiBehaviorExecutor")
    @Transactional
    @EventListener
    public void onEvent(BehaviorEventIngested ingested) {
        if (!properties.getProcessing().getAggregation().isEnabled()) {
            return;
        }
        BehaviorEvent event = ingested.event();
        if (event.getUserId() == null) {
            return;
        }
        try {
            updateMetrics(event);
        } catch (Exception ex) {
            log.warn("Failed to update metrics for event {}: {}", event.getId(), ex.getMessage());
        }
    }

    private void updateMetrics(BehaviorEvent event) {
        UUID userId = event.getUserId();
        LocalDate metricDate = event.getTimestamp().toLocalDate();
        BehaviorMetrics metrics = metricsRepository.findByUserIdAndMetricDate(userId, metricDate)
            .orElseGet(() -> BehaviorMetrics.builder().userId(userId).metricDate(metricDate).build());

        switch (event.getEventType()) {
            case VIEW, NAVIGATION -> metrics.incrementView();
            case CLICK -> metrics.incrementClick();
            case SEARCH -> metrics.incrementSearch();
            case ADD_TO_CART -> metrics.incrementAddToCart();
            case FEEDBACK -> metrics.incrementFeedback();
            case PURCHASE -> {
                double amount = extractAmount(event);
                metrics.incrementPurchase(amount);
            }
            default -> {
                // ignore
            }
        }
        metricsRepository.save(metrics);
    }

    private double extractAmount(BehaviorEvent event) {
        if (event.getMetadata() == null) {
            return 0.0;
        }
        Object amount = event.getMetadata().get("amount");
        if (amount instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return amount != null ? Double.parseDouble(amount.toString()) : 0.0;
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }
}
