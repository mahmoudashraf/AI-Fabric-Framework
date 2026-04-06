package com.ai.fabric.platform.backend.secret.model;

public record PlatformSecretSummary(
    String name,
    String displayName,
    String description,
    boolean required,
    boolean present,
    String source,
    String updatedAt,
    String scopeType,
    String ownerType,
    String secretPurpose,
    String deploymentId,
    boolean managedByPlatform,
    String cleanupPolicy
) {
}
