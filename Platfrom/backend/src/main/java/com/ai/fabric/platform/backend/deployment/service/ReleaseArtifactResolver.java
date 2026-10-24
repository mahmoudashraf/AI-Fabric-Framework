package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import org.springframework.stereotype.Service;

/** Resolves only the immutable artifact selected for this release, never a repair override. */
@Service
public class ReleaseArtifactResolver {
    private final DeploymentSourceArtifactService artifacts;

    public ReleaseArtifactResolver(DeploymentSourceArtifactService artifacts) {
        this.artifacts = artifacts;
    }

    public DeploymentSourceArtifactEntity resolve(DeploymentReleaseEntity release) {
        if (release == null || release.getSourceArtifactId() == null || release.getSourceArtifactId().isBlank())
            throw new IllegalStateException("RELEASE_ARTIFACT_REQUIRED");
        return artifacts.require(release.getSourceArtifactId());
    }
}
