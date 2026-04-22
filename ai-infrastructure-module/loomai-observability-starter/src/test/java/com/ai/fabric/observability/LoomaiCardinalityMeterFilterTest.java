package com.ai.fabric.observability;

import com.ai.fabric.observability.metrics.LoomaiCardinalityMeterFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoomaiCardinalityMeterFilterTest {

    @Test
    void deniesMetricsThatUseHighCardinalityCorrelationKeys() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new LoomaiCardinalityMeterFilter(List.of("requestId", "tenantId")));

        Counter.builder("loomai.denied")
            .tag("requestId", "req-123")
            .register(registry);
        Counter.builder("loomai.allowed")
            .tag("result", "success")
            .register(registry);

        assertThat(registry.find("loomai.denied").counter()).isNull();
        assertThat(registry.find("loomai.allowed").counter()).isNotNull();
    }
}
