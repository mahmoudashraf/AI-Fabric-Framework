package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record CreateDeploymentRequest(
    @NotBlank String name,
    @NotBlank String environment,
    @NotBlank String templateId,
    String curatedModuleId,
    String vectorProvisioningMode,
    String customerId,
    String tenantId,
    @NotBlank String behaviorType
) {
    public CreateDeploymentRequest(String name, String environment, String templateId) {
        this(name, environment, templateId, null, null, null, null, "CONVERSATIONAL");
    }

    public CreateDeploymentRequest(String name,
                                   String environment,
                                   String templateId,
                                   String curatedModuleId) {
        this(name, environment, templateId, curatedModuleId, null, null, null, "CONVERSATIONAL");
    }

    public CreateDeploymentRequest(String name,
                                   String environment,
                                   String templateId,
                                   String curatedModuleId,
                                   String vectorProvisioningMode) {
        this(name, environment, templateId, curatedModuleId, vectorProvisioningMode, null, null, "CONVERSATIONAL");
    }

    public CreateDeploymentRequest(String name,
                                   String environment,
                                   String templateId,
                                   String curatedModuleId,
                                   String vectorProvisioningMode,
                                   String customerId,
                                   String tenantId) {
        this(name, environment, templateId, curatedModuleId, vectorProvisioningMode, customerId, tenantId, "CONVERSATIONAL");
    }
}
