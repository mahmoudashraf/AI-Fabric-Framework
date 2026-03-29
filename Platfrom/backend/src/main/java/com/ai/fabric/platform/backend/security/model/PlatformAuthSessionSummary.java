package com.ai.fabric.platform.backend.security.model;

public record PlatformAuthSessionSummary(
    boolean enabled,
    String headerName,
    boolean authenticated,
    String actorId,
    String role,
    boolean canManageSecrets,
    boolean canOperateDeployments
) {
}
