package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

public record ApproveDeploymentBehaviorReadinessRequest(
    @NotEmpty List<@Valid DeploymentBehaviorReadinessEvidenceInput> evidence,
    @NotBlank String approvalNote,
    Instant expiresAt
) {
}
