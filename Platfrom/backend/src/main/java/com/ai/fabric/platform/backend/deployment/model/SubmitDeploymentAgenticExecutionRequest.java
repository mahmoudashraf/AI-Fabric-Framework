package com.ai.fabric.platform.backend.deployment.model;

public record SubmitDeploymentAgenticExecutionRequest(
    String question,
    String idempotencyKey
) {
}
