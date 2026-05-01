package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PatchDeploymentTargetProfileRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentProvisioningServiceTargetProfileTest {

    @Test
    void provisionsThroughProviderRegistryUsingResolvedTargetProfile() {
        DeploymentTargetProfileService targetProfileService = mock(DeploymentTargetProfileService.class);
        DeploymentTargetProfileEntity profile = profile("dtp-railway-api-default", DeploymentProviderType.RAILWAY_API);
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        FakeProvider railwayApi = new FakeProvider(DeploymentProviderType.RAILWAY_API, "https://runtime.example");
        FakeProvider railwayStub = new FakeProvider(DeploymentProviderType.RAILWAY_STUB, "https://stub.example");

        when(targetProfileService.resolveForRelease(release)).thenReturn(profile);

        DeploymentProvisioningService service = new DeploymentProvisioningService(
            targetProfileService,
            new DeploymentProviderRegistry(List.of(railwayStub, railwayApi))
        );

        ProvisioningResult result = service.provision(
            new DeploymentEntity(),
            new DeploymentVersionEntity(),
            release,
            null
        );

        assertThat(result.target()).isEqualTo("RAILWAY_API");
        assertThat(result.runtimeBaseUrl()).isEqualTo("https://runtime.example");
        verify(targetProfileService).applyProfileToRelease(release, profile);
    }

    @Test
    void failsBeforeProvisioningWhenProfileProviderHasNoAdapter() {
        DeploymentTargetProfileService targetProfileService = mock(DeploymentTargetProfileService.class);
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        DeploymentTargetProfileEntity coolifyProfile = profile("dtp-coolify-staging", DeploymentProviderType.COOLIFY);

        when(targetProfileService.resolveForRelease(release)).thenReturn(coolifyProfile);

        DeploymentProvisioningService service = new DeploymentProvisioningService(
            targetProfileService,
            new DeploymentProviderRegistry(List.of(new FakeProvider(DeploymentProviderType.RAILWAY_STUB, "https://stub.example")))
        );

        assertThatThrownBy(() -> service.provision(
            new DeploymentEntity(),
            new DeploymentVersionEntity(),
            release,
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No provisioning provider registered")
            .hasMessageContaining("COOLIFY");
    }

    @Test
    void selectedTargetRemainsLegacyProviderNameForRailwayCompatibility() {
        DeploymentTargetProfileService targetProfileService = mock(DeploymentTargetProfileService.class);
        when(targetProfileService.resolveDefaultRuntimeProfile())
            .thenReturn(profile("dtp-railway-stub-default", DeploymentProviderType.RAILWAY_STUB));

        DeploymentProvisioningService service = new DeploymentProvisioningService(
            targetProfileService,
            new DeploymentProviderRegistry(List.of(new FakeProvider(DeploymentProviderType.RAILWAY_STUB, "https://stub.example")))
        );

        assertThat(service.selectedTarget()).isEqualTo("RAILWAY_STUB");
        assertThat(service.selectedTargetProfile().getId()).isEqualTo("dtp-railway-stub-default");
    }

    @Test
    void patchProfileActivatesProfileAndCanMakeItRuntimeDefault() {
        DeploymentTargetProfileRepository repository = mock(DeploymentTargetProfileRepository.class);
        DeploymentTargetProfileEntity coolify = profile("dtp-coolify-staging", DeploymentProviderType.COOLIFY);
        DeploymentTargetProfileEntity production = profile("dtp-coolify-production", DeploymentProviderType.COOLIFY);
        DeploymentTargetProfileEntity railway = profile("dtp-railway-api-default", DeploymentProviderType.RAILWAY_API);
        coolify.setActive(false);
        coolify.setDefaultForRuntime(false);
        coolify.setDefaultForRestartableServices(false);
        production.setDefaultForRuntime(true);
        production.setDefaultForRestartableServices(false);
        railway.setDefaultForRuntime(true);
        railway.setDefaultForRestartableServices(true);
        when(repository.findById("dtp-coolify-staging")).thenReturn(Optional.of(coolify));
        when(repository.findAll()).thenReturn(List.of(coolify, production, railway));
        when(repository.save(any(DeploymentTargetProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentTargetProfileService service = new DeploymentTargetProfileService(
            provisioningProperties(),
            repository,
            new ObjectMapper()
        );

        var summary = service.patchProfile(
            "dtp-coolify-staging",
            new PatchDeploymentTargetProfileRequest(true, true, null)
        );

        assertThat(summary.active()).isTrue();
        assertThat(summary.defaultForRuntime()).isTrue();
        assertThat(production.isDefaultForRuntime()).isFalse();
        assertThat(railway.isDefaultForRuntime()).isFalse();
        verify(repository).save(production);
        verify(repository).save(railway);
        verify(repository).save(coolify);
    }

    @Test
    void defaultRuntimeProfileUsesGlobalActiveDefaultBeforeLegacyFallback() {
        DeploymentTargetProfileRepository repository = mock(DeploymentTargetProfileRepository.class);
        DeploymentTargetProfileEntity coolify = profile("dtp-coolify-staging", DeploymentProviderType.COOLIFY);
        when(repository.findFirstByActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc())
            .thenReturn(Optional.of(coolify));

        DeploymentTargetProfileService service = new DeploymentTargetProfileService(
            provisioningProperties(),
            repository,
            new ObjectMapper()
        );

        DeploymentTargetProfileEntity resolved = service.resolveDefaultRuntimeProfile();

        assertThat(resolved.getId()).isEqualTo("dtp-coolify-staging");
        assertThat(resolved.getProviderType()).isEqualTo(DeploymentProviderType.COOLIFY);
    }

    @Test
    void patchProfileRejectsDefaultWhenProfileRemainsInactive() {
        DeploymentTargetProfileRepository repository = mock(DeploymentTargetProfileRepository.class);
        DeploymentTargetProfileEntity coolify = profile("dtp-coolify-staging", DeploymentProviderType.COOLIFY);
        coolify.setActive(false);
        coolify.setDefaultForRuntime(false);
        when(repository.findById("dtp-coolify-staging")).thenReturn(Optional.of(coolify));

        DeploymentTargetProfileService service = new DeploymentTargetProfileService(
            provisioningProperties(),
            repository,
            new ObjectMapper()
        );

        assertThatThrownBy(() -> service.patchProfile(
            "dtp-coolify-staging",
            new PatchDeploymentTargetProfileRequest(null, true, null)
        ))
            .hasMessageContaining("must be active");
    }

    private static DeploymentTargetProfileEntity profile(String id, DeploymentProviderType providerType) {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId(id);
        profile.setName(id);
        profile.setProviderType(providerType);
        profile.setEnvironmentName("dev");
        profile.setActive(true);
        profile.setDefaultForRuntime(true);
        profile.setDefaultForRestartableServices(true);
        profile.setPlatformServicesAllowed(providerType != DeploymentProviderType.COOLIFY);
        profile.setSourceStrategy("GIT_SOURCE");
        profile.setProviderConfigJson("{}");
        profile.setNetworkPolicyJson("{}");
        profile.setResourceDefaultsJson("{}");
        profile.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return profile;
    }

    private static PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            false,
            false,
            0,
            null,
            null
        );
    }

    private record FakeProvider(DeploymentProviderType providerType, String runtimeBaseUrl)
        implements DeploymentProvisioningProvider {

        @Override
        public ProvisioningResult provision(DeploymentEntity deployment,
                                            DeploymentVersionEntity version,
                                            DeploymentReleaseEntity release,
                                            ProvisioningProgressTracker progressTracker) {
            return new ProvisioningResult(
                "ACTIVE",
                providerType.legacyTarget(),
                runtimeBaseUrl,
                runtimeBaseUrl + "/connector",
                "{}"
            );
        }
    }
}
