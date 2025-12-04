package com.ai.behavior.processing.analyzer;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorAlert;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AnomalyAnalyzer {

    private final BehaviorSchemaRegistry schemaRegistry;
    private final BehaviorModuleProperties properties;

    public List<BehaviorAlert> detect(List<BehaviorSignal> events, double sensitivity) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> signalsPerUser = events.stream()
            .filter(event -> event.getUserId() != null)
            .collect(Collectors.groupingBy(BehaviorSignal::getUserId, Collectors.counting()));

        List<BehaviorAlert> alerts = new ArrayList<>();
        for (BehaviorSignal event : events) {
            if (event.getUserId() == null) {
                continue;
            }
            BehaviorSignalDefinition definition = schemaRegistry.find(event.getSchemaId()).orElse(null);
            if (definition == null || !definition.hasTag("transaction")) {
                continue;
            }
            boolean highVelocity = isHighVelocity(signalsPerUser.getOrDefault(event.getUserId(), 0L), sensitivity);
            boolean highValue = isHighValue(event);
            if (highVelocity || highValue) {
                alerts.add(buildAlert(event, highVelocity, highValue));
            }
        }
        return alerts;
    }

    private boolean isHighVelocity(long count, double sensitivity) {
        long threshold = Math.max(2, Math.round(5 - (sensitivity * 4)));
        return count >= threshold;
    }

    private boolean isHighValue(BehaviorSignal event) {
        double amountThreshold = properties.getProcessing().getAnomaly().getAmountThreshold();
        return event.attributeValue("amount")
            .map(this::safeDouble)
            .filter(amount -> amount >= amountThreshold)
            .isPresent();
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

    private BehaviorAlert buildAlert(BehaviorSignal event, boolean highVelocity, boolean highValue) {
        String severity = highValue ? "CRITICAL" : (highVelocity ? "HIGH" : "MEDIUM");
        String reason = highValue ? "value_anomaly" : "velocity_anomaly";
        return BehaviorAlert.builder()
            .userId(event.getUserId())
            .behaviorSignalId(event.getId())
            .alertType(reason)
            .severity(severity)
            .message(buildMessage(highVelocity, highValue))
            .context(Map.of(
                "amount", event.attributeValue("amount").orElse("0"),
                "schemaId", event.getSchemaId(),
                "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : "n/a"
            ))
            .build();
    }

    private String buildMessage(boolean highVelocity, boolean highValue) {
        if (highValue && highVelocity) {
            return "High value transaction detected during elevated activity window";
        }
        if (highValue) {
            return "Transaction amount exceeds configured threshold";
        }
        if (highVelocity) {
            return "High velocity transactions detected for user";
        }
        return "Behavior anomaly detected";
    }
}
