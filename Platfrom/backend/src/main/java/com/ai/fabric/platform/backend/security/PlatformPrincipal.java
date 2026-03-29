package com.ai.fabric.platform.backend.security;

public record PlatformPrincipal(
    String actorId,
    PlatformRole role
) {
}
