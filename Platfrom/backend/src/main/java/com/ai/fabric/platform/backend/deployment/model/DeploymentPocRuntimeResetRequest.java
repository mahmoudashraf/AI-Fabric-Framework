package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentPocRuntimeResetRequest(
    Boolean confirm,
    String reason
) {
}
