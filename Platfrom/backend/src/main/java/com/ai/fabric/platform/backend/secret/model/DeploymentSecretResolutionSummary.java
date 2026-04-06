package com.ai.fabric.platform.backend.secret.model;

public record DeploymentSecretResolutionSummary(
    String deploymentId,
    String secretPurpose,
    String displayName,
    boolean paired,
    boolean resolved,
    String bindingMode,
    String scopeType,
    String ownerType,
    String secretName,
    String secondarySecretName,
    boolean fallbackUsed,
    String reasonCode,
    String diagnosticMessage
) {
}
