package com.ai.fabric.platform.backend.secret.model;

public record PlatformSecretSummary(
    String name,
    String displayName,
    String description,
    boolean required,
    boolean present,
    String source,
    String updatedAt
) {
}
