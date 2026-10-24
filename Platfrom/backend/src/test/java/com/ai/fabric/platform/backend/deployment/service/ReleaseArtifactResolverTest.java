package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleaseArtifactResolverTest {
    private final DeploymentSourceArtifactService artifacts = mock(DeploymentSourceArtifactService.class);
    private final ReleaseArtifactResolver resolver = new ReleaseArtifactResolver(artifacts);

    @Test void resolvesOnlyTheExactImmutableReleaseArtifactAndIgnoresLegacyOverrideDetails() {
        var release = new DeploymentReleaseEntity(); release.setSourceArtifactId("original-artifact");
        release.setProvisioningDetailsJson("{\"effectiveRuntimeArtifactId\":\"repair-artifact\",\"runtimeArtifactRepair\":{\"sourceArtifactId\":\"repair-artifact\"}}");
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setId("original-artifact");
        when(artifacts.require("original-artifact")).thenReturn(artifact);
        assertThat(resolver.resolve(release)).isSameAs(artifact);
        assertThat(release.getSourceArtifactId()).isEqualTo("original-artifact");
        verify(artifacts).require("original-artifact");
        verifyNoMoreInteractions(artifacts);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingArtifactNeverFallsBackToRepairOrLatestBuild(String id) {
        var release = new DeploymentReleaseEntity(); release.setSourceArtifactId(id);
        assertThatThrownBy(() -> resolver.resolve(release)).hasMessage("RELEASE_ARTIFACT_REQUIRED");
        verifyNoInteractions(artifacts);
    }

    @Test void missingReleaseCannotResolveAnArtifact() {
        assertThatThrownBy(() -> resolver.resolve(null)).hasMessage("RELEASE_ARTIFACT_REQUIRED");
        verifyNoInteractions(artifacts);
    }

    @Test void missingSelectedArtifactPropagatesWithoutTryingAnotherArtifact() {
        var release = new DeploymentReleaseEntity(); release.setSourceArtifactId("unavailable");
        var failure = new IllegalStateException("missing immutable artifact");
        when(artifacts.require("unavailable")).thenThrow(failure);
        assertThatThrownBy(() -> resolver.resolve(release)).isSameAs(failure);
        verify(artifacts).require("unavailable");
        verifyNoMoreInteractions(artifacts);
    }
}
