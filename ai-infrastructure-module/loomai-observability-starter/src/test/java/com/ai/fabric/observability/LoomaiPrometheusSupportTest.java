package com.ai.fabric.observability;

import com.ai.fabric.observability.config.LoomaiObservabilityAutoConfiguration;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import static org.assertj.core.api.Assertions.assertThat;

class LoomaiPrometheusSupportTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            SimpleMetricsExportAutoConfiguration.class,
            LoomaiObservabilityAutoConfiguration.class
        ))
        .withPropertyValues(
            "loomai.observability.product=loom-platform",
            "loomai.observability.service=platform-backend",
            "management.prometheus.metrics.export.enabled=true",
            "management.simple.metrics.export.enabled=false"
        );

    @Test
    void registersPrometheusRegistryAndScrapeEndpoint() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CollectorRegistry.class);
            assertThat(context).hasSingleBean(PrometheusMeterRegistry.class);
            assertThat(context).hasSingleBean(PrometheusScrapeEndpoint.class);
        });
    }
}
