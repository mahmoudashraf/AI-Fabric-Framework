package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManagedDependencyRuntimeGenerationEvidenceProviderTest {
    private final ManagedDependencyLifecycleService dependencies = mock(ManagedDependencyLifecycleService.class);
    private final ManagedDependencyRuntimeGenerationEvidenceProvider provider =
        new ManagedDependencyRuntimeGenerationEvidenceProvider(dependencies);

    @Test void delegatesExactReleaseAndTargetScopeWithoutManufacturingEvidence() {
        var deployment = deployment();
        var release = release();
        when(dependencies.quiesceForRuntimeGeneration("dep-1", "staging", "release-old", "release-new", "v1"))
            .thenReturn(new ManagedDependencyLifecycleService.RuntimeGenerationEvidence(
                Map.of("HUMAN_INBOX", "managed-dependency-generation:proof")
            ));

        var evidence = provider.quiesce(deployment, "release-old", release);
        assertThat(evidence.readbacks()).containsEntry("HUMAN_INBOX", "managed-dependency-generation:proof");
        provider.resumeAfterAdoption(deployment, "release-old", release);

        verify(dependencies).resumeAfterRuntimeGeneration(
            "dep-1", "staging", "release-old", "release-new", "v1"
        );
    }

    @Test void rejectsForeignReleaseScope() {
        var release = release();
        release.setDeploymentId("dep-other");
        assertThatThrownBy(() -> provider.quiesce(deployment(), "release-old", release))
            .hasMessage("RUNTIME_GENERATION_DEPENDENCY_SCOPE_INVALID");
        verifyNoInteractions(dependencies);
    }

    private DeploymentEntity deployment() {
        var deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        return deployment;
    }

    private DeploymentReleaseEntity release() {
        var release = new DeploymentReleaseEntity();
        release.setId("release-new");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("staging");
        release.setDeploymentVersionId("v1");
        return release;
    }
}
