package com.ai.fabric.platform.backend.deployment.model;

public record DeleteDeploymentRequest(
    Boolean hardDelete,
    String approvalId,
    String reason
) {
}
