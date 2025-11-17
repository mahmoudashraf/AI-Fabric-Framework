package com.ai.behavior.processing.analyzer;

import com.ai.behavior.model.BehaviorAlert;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AnomalyAnalyzer {

    public List<BehaviorAlert> detect(List<BehaviorEvent> events, double sensitivity) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> purchasesPerUser = events.stream()
            .filter(event -> event.getUserId() != null)
            .collect(Collectors.groupingBy(BehaviorEvent::getUserId, Collectors.counting()));

        List<BehaviorAlert> alerts = new ArrayList<>();
        for (BehaviorEvent event : events) {
            if (event.getUserId() == null) {
                continue;
            }
            boolean highVelocity = isHighVelocity(purchasesPerUser.getOrDefault(event.getUserId(), 0L), sensitivity);
            boolean highValue = isHighValue(event);
            if (highVelocity || highValue) {
                alerts.add(buildAlert(event, highVelocity, highValue));
            }
        }
        return alerts;
    }

    private boolean isHighVelocity(long count, double sensitivity) {
        long threshold = Math.max(3, Math.round(6 - (sensitivity * 5)));
        return count >= threshold;
    }

    private boolean isHighValue(BehaviorEvent event) {
        return event.metadataValue("amount")
            .map(this::safeDouble)
            .filter(amount -> amount >= 10000)
            .isPresent();
    }

    private double safeDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private BehaviorAlert buildAlert(BehaviorEvent event, boolean highVelocity, boolean highValue) {
        String severity = highValue ? "CRITICAL" : (highVelocity ? "HIGH" : "MEDIUM");
        String reason = highValue ? "purchase_value_anomaly" : "velocity_anomaly";
        return BehaviorAlert.builder()
            .userId(event.getUserId())
            .behaviorEventId(event.getId())
            .alertType(reason)
            .severity(severity)
            .message(buildMessage(event, highVelocity, highValue))
            .context(Map.of(
                "amount", event.metadataValue("amount").orElse("0"),
                "eventType", event.getEventType() != null ? event.getEventType().name() : EventType.CUSTOM.name(),
                "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : "n/a"
            ))
            .build();
    }

    private String buildMessage(BehaviorEvent event, boolean highVelocity, boolean highValue) {
        if (highValue && highVelocity) {
            return "High value purchase performed during suspicious velocity window";
        }
        if (highValue) {
            return "Purchase amount exceeds configured threshold";
        }
        if (highVelocity) {
            return "High velocity purchases detected for user";
        }
        return "Behavior anomaly detected";
    }
}
