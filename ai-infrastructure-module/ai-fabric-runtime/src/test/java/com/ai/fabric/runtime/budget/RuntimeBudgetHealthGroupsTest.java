package com.ai.fabric.runtime.budget;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroup;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;

class RuntimeBudgetHealthGroupsTest {
    @Test
    void budgetIsRequiredForReadinessButExplicitlyExcludedFromLiveness() {
        var primary = mock(HealthEndpointGroup.class);
        var readiness = mock(HealthEndpointGroup.class);
        var liveness = mock(HealthEndpointGroup.class);
        when(readiness.isMember("readinessState")).thenReturn(true);
        when(liveness.isMember("livenessState")).thenReturn(true);
        when(liveness.isMember("providerBudget")).thenReturn(true);
        var before = HealthEndpointGroups.of(primary, Map.of("readiness", readiness, "liveness", liveness));
        var after = new RuntimeBudgetHealthGroups().postProcessHealthEndpointGroups(before);
        assertThat(after.get("readiness").isMember("providerBudget")).isTrue();
        assertThat(after.get("readiness").isMember("readinessState")).isTrue();
        assertThat(after.get("liveness").isMember("providerBudget")).isFalse();
        assertThat(after.get("liveness").isMember("livenessState")).isTrue();
        assertThat(after.getPrimary()).isSameAs(primary);
        assertThat(after.get("readiness").getHttpCodeStatusMapper()).isSameAs(readiness.getHttpCodeStatusMapper());
    }
}
