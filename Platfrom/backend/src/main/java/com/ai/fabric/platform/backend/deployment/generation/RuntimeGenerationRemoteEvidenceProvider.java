package com.ai.fabric.platform.backend.deployment.generation;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;

/** Authoritative R5 readback for remote obligations. No default success implementation exists. */
public interface RuntimeGenerationRemoteEvidenceProvider {
    DeploymentRuntimeGenerationService.RemoteDependencyEvidence quiesce(
        DeploymentEntity deployment,
        String previousReleaseId,
        com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity nextRelease
    );

    void resumeAfterAdoption(
        DeploymentEntity deployment,
        String previousReleaseId,
        com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity nextRelease
    );
}
