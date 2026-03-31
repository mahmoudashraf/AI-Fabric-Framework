package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record CreateDeploymentRequest(
    @NotBlank String name,
    @NotBlank String environment,
    @NotBlank String templateId,
    String curatedModuleId,
    String vectorProvisioningMode
) {
    public CreateDeploymentRequest(String name, String environment, String templateId) {
        this(name, environment, templateId, null, null);
    }

    public CreateDeploymentRequest(String name,
                                   String environment,
                                   String templateId,
                                   String curatedModuleId) {
        this(name, environment, templateId, curatedModuleId, null);
    }
}
