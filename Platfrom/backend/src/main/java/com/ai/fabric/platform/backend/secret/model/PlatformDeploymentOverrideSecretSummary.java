package com.ai.fabric.platform.backend.secret.model;

public record PlatformDeploymentOverrideSecretSummary(
    String name,
    String displayName,
    String secretPurpose,
    String deploymentId,
    String scopeType,
    String ownerType,
    String cleanupPolicy,
    boolean present,
    int bindingCount,
    String updatedAt
) {
}
