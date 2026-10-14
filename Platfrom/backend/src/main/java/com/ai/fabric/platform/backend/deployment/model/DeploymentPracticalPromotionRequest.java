package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DeploymentPracticalPromotionRequest(
    @NotBlank @Size(max = 128) String consumerId,
    @NotNull @PositiveOrZero Long expectedBindingRevision,
    @NotBlank @Size(max = 64) String stagingReleaseId,
    @NotBlank @Size(max = 64) String productionTargetProfileId,
    @Size(max = 64) String candidateFingerprint,
    @Size(max = 128) String operationKey
) {
}
