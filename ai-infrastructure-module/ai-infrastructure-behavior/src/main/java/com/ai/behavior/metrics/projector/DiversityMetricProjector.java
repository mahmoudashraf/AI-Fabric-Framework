package com.ai.behavior.metrics.projector;

import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class DiversityMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "diversityMetricProjector";

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        return definition != null;
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        if (definition == null) {
            return;
        }
        accumulator.increment("diversity.events");

        accumulator.addDistinctAttributeValue("diversity.schemas", sanitize(definition.getId()));
        if (StringUtils.hasText(definition.getDomain())) {
            accumulator.addDistinctAttributeValue("diversity.domains", sanitize(definition.getDomain()));
        }
        if (!CollectionUtils.isEmpty(definition.getTags())) {
            definition.getTags().forEach(tag ->
                accumulator.addDistinctAttributeValue("diversity.tags", sanitize(tag))
            );
        }

        int uniqueSchemas = accumulator.distinctAttributeCount("diversity.schemas");
        int uniqueDomains = accumulator.distinctAttributeCount("diversity.domains");
        int uniqueTags = accumulator.distinctAttributeCount("diversity.tags");

        accumulator.set("diversity.schemas_count", uniqueSchemas);
        accumulator.set("diversity.domains_count", uniqueDomains);
        accumulator.set("diversity.tags_count", uniqueTags);

        double schemaFactor = Math.min(1.0d, uniqueSchemas / 10.0d);
        double domainFactor = Math.min(1.0d, uniqueDomains / 5.0d);
        double tagFactor = Math.min(1.0d, uniqueTags / 15.0d);
        double diversityScore = Math.min(1.0d, (schemaFactor * 0.5d) + (domainFactor * 0.3d) + (tagFactor * 0.2d));

        accumulator.set("kpi.diversity_score", round(diversityScore));
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
