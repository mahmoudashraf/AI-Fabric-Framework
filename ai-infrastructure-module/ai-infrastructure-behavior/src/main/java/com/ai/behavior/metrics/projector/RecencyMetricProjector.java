package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class RecencyMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "recencyMetricProjector";
    private static final double LOOKBACK_HOURS = 24 * 7; // one week

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        return signal != null && signal.getTimestamp() != null;
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        LocalDateTime occurred = signal.getTimestamp();
        if (occurred == null) {
            return;
        }
        double hoursAgo = Math.max(0.0d, Duration.between(occurred, LocalDateTime.now()).toHours());
        double recencyScore = Math.max(0.0d, 1.0d - (hoursAgo / LOOKBACK_HOURS));

        double previous = accumulator.value("kpi.recency_score");
        accumulator.set("kpi.recency_score", round(Math.max(previous, recencyScore)));
        accumulator.set("recency.hours_since_last", hoursAgo);
        accumulator.increment("recency.events");
        accumulator.attribute("recency.last_seen_at", occurred.toString());

        if (definition != null && StringUtils.hasText(definition.getId())) {
            accumulator.set("recency.schema." + sanitize(definition.getId()), recencyScore);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
