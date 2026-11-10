package com.ai.fabric.runtime.tasks;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;

class ManagedTasksConfigurationTest {
    @Test void boundedDedicatedWorkersAreConfigurableAndInvalidBoundsAreRejected() {
        var config = new ManagedTaskConfiguration();
        try (var executor = config.managedTaskExecutor(2, 3)) {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaximumPoolSize()).isEqualTo(2);
            assertThat(executor.getQueue().remainingCapacity()).isEqualTo(3);
        }
        assertThatThrownBy(() -> config.managedTaskExecutor(0, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.managedTaskExecutor(2, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.managedTaskExecutor(33, 3)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void healthGroupMembershipPreservesOtherDependenciesAndLiveness() {
        var primary = mock(HealthEndpointGroup.class);
        var ready = mock(HealthEndpointGroup.class);
        var live = mock(HealthEndpointGroup.class);
        when(ready.isMember("providerBudget")).thenReturn(true);
        when(live.isMember("livenessState")).thenReturn(true);
        var after = new ManagedTasksHealthGroups().postProcessHealthEndpointGroups(
            HealthEndpointGroups.of(primary, Map.of("readiness", ready, "liveness", live)));
        assertThat(after.get("readiness").isMember("managedTasks")).isTrue();
        assertThat(after.get("readiness").isMember("providerBudget")).isTrue();
        assertThat(after.get("liveness").isMember("managedTasks")).isFalse();
        assertThat(after.get("liveness").isMember("livenessState")).isTrue();
        assertThat(after.getPrimary()).isSameAs(primary);
    }
}
