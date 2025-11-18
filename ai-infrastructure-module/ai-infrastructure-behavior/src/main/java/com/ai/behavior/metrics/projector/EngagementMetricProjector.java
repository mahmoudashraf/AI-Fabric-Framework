package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Set;

@Component
public class EngagementMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "engagementMetricProjector";

    private static final Set<String> ACTIVE_TAGS = Set.of("engagement", "experience", "conversion", "intent");

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
            if (definition.getTags().stream().map(String::toLowerCase).anyMatch(ACTIVE_TAGS::contains)) {
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

        accumulator.attribute("engagement.last_schema", definition.getId());
        if (!CollectionUtils.isEmpty(definition.getTags())) {
            accumulator.attribute("engagement.last_tags", definition.getTags());
        }

        accumulator.set("kpi.engagement_score", round(computeScore(accumulator)));
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

    private double computeScore(MetricAccumulator accumulator) {
        double total = accumulator.value(key("count", "total"));
        double active = accumulator.value(key("count", "active"));
        double transactions = accumulator.value("value.transaction_count");
        double amount = accumulator.value("value.amount_total");
        double durationSeconds = accumulator.value("duration.total_seconds");

        double baseline = Math.min(1.0d, Math.log1p(total) / 5.0d);
        double activeFactor = Math.min(1.0d, active / 25.0d);
        double transactionFactor = Math.min(1.0d, transactions / 15.0d);
        double valueFactor = Math.min(1.0d, amount / 5000.0d);
        double durationFactor = Math.min(1.0d, durationSeconds / 7200.0d);

        return (baseline * 0.35d) +
            (activeFactor * 0.2d) +
            (transactionFactor * 0.2d) +
            (valueFactor * 0.15d) +
            (durationFactor * 0.1d);
    }

    private double round(double value) {
        return Math.round(Math.min(1.0d, value) * 100.0d) / 100.0d;
    }
}
