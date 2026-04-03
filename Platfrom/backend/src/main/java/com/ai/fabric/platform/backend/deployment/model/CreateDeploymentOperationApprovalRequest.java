package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record CreateDeploymentOperationApprovalRequest(
    @NotBlank String operationType,
    String targetVersionId,
    @NotBlank String reason
) {
}
