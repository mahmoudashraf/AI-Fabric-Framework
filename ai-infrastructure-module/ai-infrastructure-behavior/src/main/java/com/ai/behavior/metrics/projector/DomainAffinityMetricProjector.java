package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DomainAffinityMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "domainAffinityMetricProjector";

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        return definition != null && StringUtils.hasText(definition.getDomain());
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        if (definition == null || !StringUtils.hasText(definition.getDomain())) {
            return;
        }
        String domain = sanitize(definition.getDomain());
        String countKey = "domain." + domain + ".count";
        accumulator.increment(countKey, 1.0d);

        double total = Math.max(1.0d, accumulator.value("count.total"));
        double domainCount = accumulator.value(countKey);
        accumulator.set("domain." + domain + ".share", clamp(domainCount / total));
    }

    @Override
    public String getName() {
        return NAME;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_").toLowerCase();
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
