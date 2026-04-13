package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocRuntimeAuthContextSummary(
    String subjectId,
    String subjectType,
    String authMode,
    String callerType,
    String sessionId,
    String deploymentId,
    String customerId,
    String tenantId,
    String issuer,
    String expiresAt,
    List<String> grantedScopes,
    List<String> warnings
) {
}
