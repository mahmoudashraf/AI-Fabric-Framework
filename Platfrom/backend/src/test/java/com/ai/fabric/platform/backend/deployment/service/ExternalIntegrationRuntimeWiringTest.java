package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.ReleaseEnvironment;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationResolver;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalIntegrationRuntimeWiringTest {
    @Test void onlyConnectorGetsFrozenValuesAndSecretFlagsComeFromTheTypedProjection() {
        var fixture = new Fixture();
        when(fixture.bindings.releaseEnvironmentForProvisioning(fixture.deployment, fixture.version, fixture.release))
            .thenReturn(new ReleaseEnvironment(Map.of("MANAGED_EXTERNAL_INTEGRATION_CONFIG", "https://provider.example.com"),
                Map.of("MANAGED_EXTERNAL_INTEGRATION_CREDENTIAL", "SCOPED_CREDENTIAL_REFERENCE")));
        when(fixture.vault.resolveSecretForDeployment("SCOPED_CREDENTIAL_REFERENCE", fixture.deployment.getId(),
            fixture.release.getTargetProfileId())).thenReturn("synthetic-private-value");
        var env = new LinkedHashMap<String, CoolifyEnvVar>();
        fixture.provider.mergeFrozenExternalIntegrationEnvironment(fixture.deployment, fixture.version, fixture.release, "runtime", env);
        assertThat(env).isEmpty();
        verifyNoInteractions(fixture.bindings, fixture.vault);
        fixture.provider.mergeFrozenExternalIntegrationEnvironment(fixture.deployment, fixture.version, fixture.release, "connector", env);
        assertThat(env.get("MANAGED_EXTERNAL_INTEGRATION_CONFIG").shownOnce()).isFalse();
        assertThat(env.get("MANAGED_EXTERNAL_INTEGRATION_CREDENTIAL").shownOnce()).isTrue();
        assertThat(env.get("MANAGED_EXTERNAL_INTEGRATION_CREDENTIAL").value()).isEqualTo("synthetic-private-value");
        assertThat(env.values().stream().map(CoolifyEnvVar::value)).doesNotContain("SCOPED_CREDENTIAL_REFERENCE");
        verify(fixture.vault).resolveSecretForDeployment("SCOPED_CREDENTIAL_REFERENCE", fixture.deployment.getId(),
            fixture.release.getTargetProfileId());
        verify(fixture.vault, never()).resolveSecret(anyString());
    }

    @Test void missingCredentialOrCollidingAliasStopsEnvironmentGeneration() {
        var fixture = new Fixture();
        when(fixture.bindings.releaseEnvironmentForProvisioning(any(), any(), any()))
            .thenReturn(new ReleaseEnvironment(Map.of(), Map.of("ALIAS", "SCOPED_REFERENCE")));
        assertThatThrownBy(() -> fixture.provider.mergeFrozenExternalIntegrationEnvironment(fixture.deployment,
            fixture.version, fixture.release, "connector", new LinkedHashMap<>())).hasMessageContaining("Missing managed target authority secret");
        var env = new LinkedHashMap<String, CoolifyEnvVar>();
        env.put("ALIAS", new CoolifyEnvVar("ALIAS", "unexpected", false, true, false, false));
        assertThatThrownBy(() -> fixture.provider.mergeFrozenExternalIntegrationEnvironment(fixture.deployment,
            fixture.version, fixture.release, "connector", env)).hasMessage("EXTERNAL_INTEGRATION_ENVIRONMENT_COLLISION");
    }

    @Test void ordinaryDeploymentNeverAcquiresAnExternalServiceRequirement() {
        var fixture = new Fixture(); fixture.version.setCompositionProvenanceJson("{}");
        fixture.provider.mergeFrozenExternalIntegrationEnvironment(fixture.deployment, fixture.version, fixture.release,
            "connector", new LinkedHashMap<>());
        verifyNoInteractions(fixture.bindings, fixture.vault);
    }

    private static final class Fixture {
        final ExternalIntegrationResolver bindings = mock(ExternalIntegrationResolver.class);
        final PlatformSecretService vault = mock(PlatformSecretService.class);
        final CoolifyDeploymentProvider provider = mock(CoolifyDeploymentProvider.class, CALLS_REAL_METHODS);
        final DeploymentEntity deployment = new DeploymentEntity();
        final DeploymentVersionEntity version = new DeploymentVersionEntity();
        final DeploymentReleaseEntity release = new DeploymentReleaseEntity();

        Fixture() {
            deployment.setId("deployment"); version.setId("version");
            version.setCompositionProvenanceJson("{\"externalIntegrationProfiles\":[{\"profileId\":\"reviewed\"}]}");
            release.setId("release"); release.setTargetProfileId("target"); release.setRuntimeAuthorityFingerprint("authority");
            ReflectionTestUtils.setField(provider, "objectMapper", new ObjectMapper());
            ReflectionTestUtils.setField(provider, "externalIntegrations", bindings);
            ReflectionTestUtils.setField(provider, "platformSecretService", vault);
        }
    }
}
