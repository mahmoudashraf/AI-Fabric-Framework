package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.KpiKeys;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class RecencyMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "recencyMetricProjector";

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        return signal != null && signal.getTimestamp() != null;
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        LocalDateTime timestamp = signal.getTimestamp();
        if (timestamp == null) {
            return;
        }
        double hoursSince = Math.max(0.0d, Duration.between(timestamp, LocalDateTime.now()).toMinutes() / 60.0d);
        double score = clamp(1.0d - (hoursSince / 168.0d));
        accumulator.set(KpiKeys.RECENCY_SCORE, score);
        accumulator.min(KpiKeys.RECENCY_HOURS_SINCE_LAST, hoursSince);
    }

    @Override
    public String getName() {
        return NAME;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0d;
        }
        if (value < 0) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
