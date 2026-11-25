package com.ai.fabric.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;
import ai.fabric.config.OrchestrationProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Platform-emitted environment names must bind the actual released SDK contract. */
class ConversationalAutonomyBindingTest {
    @Test void publishedEnvironmentNamesBindActualExecutionLimits() {
        var environment = new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of(
            "AI_ORCHESTRATION_AUTONOMY_ENABLED", "true",
            "AI_ORCHESTRATION_AUTONOMY_DEFAULT_ENABLED", "false",
            "AI_ORCHESTRATION_AUTONOMY_MAX_ELAPSED_SECONDS", "90",
            "AI_ORCHESTRATION_AUTONOMY_MAX_WRITES_PER_SEGMENT", "0",
            "AI_ORCHESTRATION_AUTONOMY_REPLAN_NON_AUTHORITY_READ_FAILURES", "false"));
        var properties = new Binder(ConfigurationPropertySources.from(environment))
            .bind("ai.orchestration", Bindable.of(OrchestrationProperties.class)).get();
        assertThat(properties.getAutonomy().isEnabled()).isTrue();
        assertThat(properties.getAutonomy().isDefaultEnabled()).isFalse();
        assertThat(properties.getAutonomy().getMaxElapsedSeconds()).isEqualTo(90);
        assertThat(properties.getAutonomy().getMaxWritesPerSegment()).isZero();
        assertThat(properties.getAutonomy().isReplanNonAuthorityReadFailures()).isFalse();
    }
}
