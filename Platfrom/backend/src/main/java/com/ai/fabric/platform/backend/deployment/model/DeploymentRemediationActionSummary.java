package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentRemediationActionSummary(
    String key,
    String label,
    String description,
    String category,
    String severity,
    String requiredRole,
    boolean available,
    boolean requiresConfirmation,
    boolean requiresReason,
    boolean requiresApproval,
    String blockedReason,
    String confirmationText
) {
}
