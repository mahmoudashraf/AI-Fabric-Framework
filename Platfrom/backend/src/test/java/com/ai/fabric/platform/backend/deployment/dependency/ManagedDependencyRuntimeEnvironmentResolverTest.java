package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManagedDependencyRuntimeEnvironmentResolverTest {
    private final CustomerOfferDeploymentRepository offers = mock(CustomerOfferDeploymentRepository.class);
    private final ManagedDependencyLifecycleService lifecycle = mock(ManagedDependencyLifecycleService.class);
    private final ManagedDependencyRuntimeEnvironmentResolver resolver =
        new ManagedDependencyRuntimeEnvironmentResolver(offers, lifecycle);

    @Test
    void ordinaryDeploymentReceivesNoManagedEnvironment() {
        var result = resolver.resolve("deployment", "target", "version", null);
        assertThat(result.runtime()).isEmpty();
        assertThat(result.connector()).isEmpty();
        verifyNoInteractions(lifecycle);
    }

    @Test
    void customerOfferRequiresExactFrozenAuthorityAndDelegatesProjection() {
        when(offers.existsById("deployment")).thenReturn(true);
        assertThatThrownBy(() -> resolver.resolve("deployment", "target", "version", null))
            .hasMessageContaining("DEPENDENCY_RELEASE_BINDING_REQUIRED");

        var expected = new ManagedDependencyLifecycleService.RuntimeEnvironmentProjection(
            Map.of("RUNTIME_VALUE", "runtime"), Map.of("CONNECTOR_VALUE", "connector"));
        when(lifecycle.runtimeEnvironment("deployment", "target", "version", "a".repeat(64)))
            .thenReturn(expected);
        assertThat(resolver.resolve("deployment", "target", "version", "a".repeat(64))).isEqualTo(expected);
    }
}
