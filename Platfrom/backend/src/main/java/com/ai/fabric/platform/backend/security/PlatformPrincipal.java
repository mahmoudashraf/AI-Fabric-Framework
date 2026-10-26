package com.ai.fabric.platform.backend.security;

public record PlatformPrincipal(
    String actorId,
    PlatformRole role,
    String displayName,
    String authenticationMode,
    String platformUserId
) {
    public PlatformPrincipal(String actorId, PlatformRole role, String displayName, String authenticationMode) {
        this(actorId, role, displayName, authenticationMode, null);
    }
}
