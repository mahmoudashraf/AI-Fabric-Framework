package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.Size;

public record DeploymentPracticalPromotionRollbackRequest(
    @Size(max = 128) String consumerId,
    @Size(max = 64) String rollbackDeploymentId,
    @Size(max = 64) String rollbackReleaseId,
    @Size(max = 64) String rollbackTargetProfileId,
    @Size(max = 1000) String reason
) {
}
