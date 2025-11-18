package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class EngagementMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "engagementMetricProjector";

    private static final List<String> ACTIVE_TAGS = List.of("engagement", "experience", "conversion", "intent");

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        return definition != null && definition.getTags() != null;
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        // total counts per schema
        accumulator.increment(key("count", "total"), 1.0d);
        accumulator.increment(key("schema", sanitize(definition.getId())), 1.0d);

        // tag-derived counts
        if (definition.getTags() != null) {
            definition.getTags().forEach(tag ->
                accumulator.increment(key("tag", sanitize(tag)), 1.0d)
            );
            if (definition.getTags().stream().anyMatch(ACTIVE_TAGS::contains)) {
                accumulator.increment(key("count", "active"), 1.0d);
            }
        }

        // value-oriented metrics
        double amount = signal.attributeValue("amount")
            .filter(StringUtils::hasText)
            .map(this::safeDouble)
            .orElse(0.0d);
        if (amount > 0) {
            accumulator.increment("value.amount_total", amount);
            accumulator.increment("value.transaction_count", 1.0d);
        }

        signal.attributeValue("durationSeconds")
            .filter(StringUtils::hasText)
            .map(this::safeDouble)
            .ifPresent(duration -> accumulator.increment("duration.total_seconds", duration));

        // recency — seconds since event
        if (signal.getTimestamp() != null) {
            double hoursAgo = Duration.between(signal.getTimestamp(), LocalDateTime.now()).toHours();
            double recencyScore = Math.max(0.0, 1.0 - (hoursAgo / 168.0));
            accumulator.set("recency.last_score", recencyScore);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    private double safeDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String key(String prefix, String suffix) {
        return prefix + "." + suffix;
    }
}
