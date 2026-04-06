package com.ai.fabric.platform.backend.secret.model;

public record DeploymentProviderSecretBindingSummary(
    String id,
    String deploymentId,
    String secretPurpose,
    String displayName,
    String bindingMode,
    String secretName,
    String secondarySecretName,
    DeploymentSecretResolutionSummary effectiveResolution,
    String createdAt,
    String updatedAt
) {
}
