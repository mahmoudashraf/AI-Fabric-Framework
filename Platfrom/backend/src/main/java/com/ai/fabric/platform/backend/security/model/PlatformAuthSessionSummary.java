package com.ai.fabric.platform.backend.security.model;

public record PlatformAuthSessionSummary(
    boolean enabled,
    String headerName,
    boolean authenticated,
    String actorId,
    String displayName,
    String role,
    String authenticationMode,
    boolean sessionAuthEnabled,
    boolean apiKeyAuthEnabled,
    boolean canManageSecrets,
    boolean canOperateDeployments
) {
}
