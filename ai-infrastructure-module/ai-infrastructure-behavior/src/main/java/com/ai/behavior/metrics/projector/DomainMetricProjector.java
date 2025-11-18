package com.ai.behavior.metrics.projector;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.metrics.BehaviorMetricProjector;
import com.ai.behavior.metrics.MetricAccumulator;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DomainMetricProjector implements BehaviorMetricProjector {

    private static final String NAME = "domainMetricProjector";

    private final BehaviorModuleProperties properties;

    @Override
    public boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition) {
        if (definition == null || !StringUtils.hasText(definition.getDomain())) {
            return false;
        }
        List<String> highlighted = properties.getProcessing().getMetrics().getHighlightedDomains();
        return CollectionUtils.isEmpty(highlighted) ||
            highlighted.stream().anyMatch(domain -> domain.equalsIgnoreCase(definition.getDomain()));
    }

    @Override
    public void project(BehaviorSignal signal,
                        BehaviorSignalDefinition definition,
                        MetricAccumulator accumulator) {
        if (definition == null || !StringUtils.hasText(definition.getDomain())) {
            return;
        }
        String domainKey = sanitize(definition.getDomain());
        accumulator.increment(key(domainKey, "count"));
        accumulator.addDistinctAttributeValue(key(domainKey, "schemas"), sanitize(definition.getId()));

        double amount = signal.attributeValue("amount")
            .filter(StringUtils::hasText)
            .map(this::safeDouble)
            .orElse(0.0d);
        if (amount > 0) {
            accumulator.increment(key(domainKey, "amount_total"), amount);
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    private String key(String domain, String suffix) {
        return "domain." + domain + "." + suffix;
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
}
