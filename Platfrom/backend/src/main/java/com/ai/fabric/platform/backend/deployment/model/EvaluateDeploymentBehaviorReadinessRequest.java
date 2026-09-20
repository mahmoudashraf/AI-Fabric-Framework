package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public record EvaluateDeploymentBehaviorReadinessRequest(
    @NotBlank String releaseId,
    @NotEmpty List<@Valid DeploymentBehaviorVerificationProofInput> behaviorProofs,
    Instant expiresAt
) {
}
