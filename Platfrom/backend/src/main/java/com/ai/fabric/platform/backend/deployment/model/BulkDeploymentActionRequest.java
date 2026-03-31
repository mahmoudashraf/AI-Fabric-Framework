package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkDeploymentActionRequest(
    @NotBlank String action,
    @NotEmpty List<@NotBlank String> deploymentIds
) {
}
