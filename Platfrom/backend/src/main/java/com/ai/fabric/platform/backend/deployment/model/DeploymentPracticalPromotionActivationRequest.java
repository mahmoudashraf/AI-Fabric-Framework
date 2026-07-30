package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.Size;

public record DeploymentPracticalPromotionActivationRequest(
    @Size(max = 64) String productionReleaseId,
    @Size(max = 128) String consumerId,
    boolean markStagingSuperseded,
    @Size(max = 1000) String reason
) {
}
