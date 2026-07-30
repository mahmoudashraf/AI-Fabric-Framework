package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.Size;

public record DeploymentPracticalPromotionRequest(
    @Size(max = 64) String versionId,
    @Size(max = 64) String stagingTargetProfileId,
    @Size(max = 64) String productionTargetProfileId,
    @Size(max = 64) String sourceArtifactId
) {
}
