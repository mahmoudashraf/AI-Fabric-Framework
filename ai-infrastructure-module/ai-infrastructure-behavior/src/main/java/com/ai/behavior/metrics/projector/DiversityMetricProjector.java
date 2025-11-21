package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.KpiKeys;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class DiversityMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "diversityMetricProjector";
    private static final String DIVERSITY_SCHEMA_PREFIX = "diversity.schema.";

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        return definition != null;
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        String schemaKey = schemaKey(definition.getId());
        Map<String, Double> snapshot = accumulator.snapshot();
        if (!snapshot.containsKey(schemaKey)) {
            accumulator.set(schemaKey, 1.0d);
        }

        long uniqueSchemas = snapshot.keySet().stream()
            .filter(key -> key.startsWith(DIVERSITY_SCHEMA_PREFIX))
            .count();
        accumulator.set(KpiKeys.DIVERSITY_UNIQUE_SCHEMA_COUNT, uniqueSchemas);
        double totalSignals = Math.max(1.0d, accumulator.value("count.total"));
        double ratio = Math.min(1.0d, uniqueSchemas / totalSignals);
        accumulator.set(KpiKeys.DIVERSITY_UNIQUE_SCHEMA_RATIO, ratio);
        accumulator.set(KpiKeys.DIVERSITY_SCORE, ratio);
    }

    @Override
    public String getName() {
        return NAME;
    }

    private String schemaKey(String schemaId) {
        String sanitized = sanitize(schemaId);
        return DIVERSITY_SCHEMA_PREFIX + sanitized;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
