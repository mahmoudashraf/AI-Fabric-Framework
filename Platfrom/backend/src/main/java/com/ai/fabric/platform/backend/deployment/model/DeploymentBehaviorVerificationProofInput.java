package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DeploymentBehaviorVerificationProofInput(
    @NotBlank String verificationPackId,
    @NotBlank String status,
    @NotBlank String evidenceRef,
    @NotEmpty List<@NotBlank String> passedChecks
) {
}
