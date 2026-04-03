package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateDeploymentCuratedModuleRequest(
    @NotBlank String curatedModuleId
) {
}
