package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorAlert;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import com.ai.behavior.storage.BehaviorAlertRepository;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetectionWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorDataProvider dataProvider;
    private final BehaviorAlertRepository alertRepository;

    @Scheduled(cron = "${ai.behavior.processing.anomaly.schedule:0 * * * * *}")
    @Transactional
    public void detect() {
        if (!properties.getProcessing().getAnomaly().isEnabled()) {
            return;
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(1);
        List<BehaviorEvent> purchases = dataProvider.query(BehaviorQuery.builder()
            .eventType(EventType.PURCHASE)
            .startTime(since)
            .limit(500)
            .build());
        if (purchases.isEmpty()) {
            return;
        }
        double sensitivity = properties.getProcessing().getAnomaly().getSensitivity();
        Map<UUID, Long> purchasesPerUser = purchases.stream()
            .filter(event -> event.getUserId() != null)
            .collect(Collectors.groupingBy(BehaviorEvent::getUserId, Collectors.counting()));

        purchases.forEach(event -> {
            boolean suspicious = isHighVelocity(purchasesPerUser.getOrDefault(event.getUserId(), 0L), sensitivity) ||
                isHighValue(event);
            if (suspicious) {
                BehaviorAlert alert = BehaviorAlert.builder()
                    .userId(event.getUserId())
                    .behaviorEventId(event.getId())
                    .alertType("purchase_anomaly")
                    .severity("HIGH")
                    .message("Suspicious purchase detected")
                    .context(Map.of(
                        "amount", event.metadataValue("amount").orElse("0"),
                        "eventType", event.getEventType().name(),
                        "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : "n/a"
                    ))
                    .build();
                alertRepository.save(alert);
            }
        });
    }

    private boolean isHighVelocity(long count, double sensitivity) {
        long threshold = Math.max(3, Math.round(6 - (sensitivity * 5)));
        return count >= threshold;
    }

    private boolean isHighValue(BehaviorEvent event) {
        return event.metadataValue("amount")
            .map(value -> {
                try {
                    return Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    return 0.0;
                }
            })
            .filter(amount -> amount >= 10000)
            .isPresent();
    }
}
