package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record UpsertDeploymentAssignmentRequest(
    @NotBlank String userId,
    @NotBlank String assignmentRole
) {
}
