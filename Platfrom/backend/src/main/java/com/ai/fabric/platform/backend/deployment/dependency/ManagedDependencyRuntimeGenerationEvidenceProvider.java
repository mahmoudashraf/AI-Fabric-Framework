package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.generation.DeploymentRuntimeGenerationService;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationRemoteEvidenceProvider;
import org.springframework.stereotype.Component;

/** Bridges target-scoped managed dependencies into the runtime generation fence. */
@Component
public class ManagedDependencyRuntimeGenerationEvidenceProvider
        implements RuntimeGenerationRemoteEvidenceProvider {
    private final ManagedDependencyLifecycleService dependencies;

    public ManagedDependencyRuntimeGenerationEvidenceProvider(ManagedDependencyLifecycleService dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public DeploymentRuntimeGenerationService.RemoteDependencyEvidence quiesce(
            DeploymentEntity deployment, String previousReleaseId, DeploymentReleaseEntity nextRelease) {
        require(deployment, nextRelease);
        var evidence = dependencies.quiesceForRuntimeGeneration(
            deployment.getId(), nextRelease.getTargetProfileId(), previousReleaseId,
            nextRelease.getId(), nextRelease.getDeploymentVersionId()
        );
        return new DeploymentRuntimeGenerationService.RemoteDependencyEvidence(evidence.readbacks());
    }

    @Override
    public void resumeAfterAdoption(
            DeploymentEntity deployment, String previousReleaseId, DeploymentReleaseEntity nextRelease) {
        require(deployment, nextRelease);
        dependencies.resumeAfterRuntimeGeneration(
            deployment.getId(), nextRelease.getTargetProfileId(), previousReleaseId,
            nextRelease.getId(), nextRelease.getDeploymentVersionId()
        );
    }

    private void require(DeploymentEntity deployment, DeploymentReleaseEntity release) {
        if (deployment == null || release == null || deployment.getId() == null
            || !deployment.getId().equals(release.getDeploymentId())
            || release.getTargetProfileId() == null || release.getDeploymentVersionId() == null) {
            throw new IllegalStateException("RUNTIME_GENERATION_DEPENDENCY_SCOPE_INVALID");
        }
    }
}
