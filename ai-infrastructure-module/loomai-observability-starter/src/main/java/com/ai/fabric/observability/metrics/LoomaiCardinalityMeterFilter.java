package com.ai.fabric.observability.metrics;

import java.util.List;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

public class LoomaiCardinalityMeterFilter implements MeterFilter {

    private final List<String> deniedTagKeys;

    public LoomaiCardinalityMeterFilter(List<String> deniedTagKeys) {
        this.deniedTagKeys = deniedTagKeys;
    }

    @Override
    public MeterFilterReply accept(Id id) {
        boolean hasDeniedKey = id.getTags().stream()
            .map(tag -> tag.getKey())
            .anyMatch(deniedTagKeys::contains);
        return hasDeniedKey ? MeterFilterReply.DENY : MeterFilterReply.NEUTRAL;
    }
}
