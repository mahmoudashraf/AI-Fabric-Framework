package com.ai.behavior.metrics;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;

public interface BehaviorMetricProjector {

    /**
     * @return true if this projector should process the given signal/definition combination.
     */
    boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition);

    /**
     * Apply metric updates for the provided signal.
     */
    void project(BehaviorSignal signal, BehaviorSignalDefinition definition, MetricAccumulator accumulator);

    /**
     * @return unique name used for configuration filtering/logging.
     */
    String getName();
}
