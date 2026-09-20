package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record DeploymentBehaviorReadinessEvidenceInput(
    @NotBlank String area,
    @NotBlank String status,
    @NotBlank String evidenceRef,
    @NotBlank String summary
) {
}
