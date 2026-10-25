package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManagedDependencyRuntimeEnvironmentResolverTest {
    private final CustomerOfferDeploymentRepository offers = mock(CustomerOfferDeploymentRepository.class);
    private final ManagedDependencyLifecycleService lifecycle = mock(ManagedDependencyLifecycleService.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
    private final ManagedDependencyRuntimeEnvironmentResolver resolver =
        new ManagedDependencyRuntimeEnvironmentResolver(offers, lifecycle, versions, specs,
            new CustomerOfferCodec(new ObjectMapper()));

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
        var version = version();
        when(versions.findById("version")).thenReturn(Optional.of(version));
        when(specs.findByTemplateVersionIdAndActiveTrue("current-template")).thenReturn(List.of(new ManagedDependencySpecEntity()));
        assertThatThrownBy(() -> resolver.resolve("deployment", "target", "version", null))
            .hasMessageContaining("DEPENDENCY_RELEASE_BINDING_REQUIRED");

        var expected = new ManagedDependencyLifecycleService.RuntimeEnvironmentProjection(
            Map.of("RUNTIME_VALUE", "runtime"), Map.of("CONNECTOR_VALUE", "connector"));
        when(lifecycle.runtimeEnvironment("deployment", "target", "version", "a".repeat(64)))
            .thenReturn(expected);
        assertThat(resolver.resolve("deployment", "target", "version", "a".repeat(64))).isEqualTo(expected);
    }

    @Test
    void removingManagedDependenciesDoesNotInjectTheOriginalTemplateEnvironmentIntoANewVersion() {
        when(offers.existsById("deployment")).thenReturn(true);
        when(versions.findById("version")).thenReturn(Optional.of(version()));
        when(specs.findByTemplateVersionIdAndActiveTrue("current-template")).thenReturn(List.of());
        var result = resolver.resolve("deployment", "target", "version", null);
        assertThat(result.runtime()).isEmpty(); assertThat(result.connector()).isEmpty();
        verify(specs).findByTemplateVersionIdAndActiveTrue("current-template");
        verifyNoInteractions(lifecycle);
    }

    private DeploymentVersionEntity version() {
        var version = new DeploymentVersionEntity(); version.setId("version");
        version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        version.setCompositionProvenanceJson("{\"marketplaceInstalls\":[{\"pluginType\":\"TEMPLATE\",\"pluginVersionId\":\"current-template\"}]}");
        return version;
    }
}
