package com.ai.fabric.platform.backend.secret.model;

public record UpsertPlatformDeploymentOverrideSecretRequest(
    String secretPurpose,
    String value,
    String deploymentId,
    String cleanupPolicy
) {
}
