package com.ai.fabric.platform.backend.deployment.model;

public record ExecuteDeploymentRemediationRequest(
    Boolean confirm,
    String reason,
    String approvalId
) {
}
