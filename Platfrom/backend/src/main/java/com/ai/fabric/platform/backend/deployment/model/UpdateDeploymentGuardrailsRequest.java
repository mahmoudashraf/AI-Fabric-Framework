package com.ai.fabric.platform.backend.deployment.model;

public record UpdateDeploymentGuardrailsRequest(
    boolean approvalRequiredForApply,
    boolean approvalRequiredForDelete
) {
}
