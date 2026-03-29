package com.ai.fabric.platform.backend.security.service;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;

import java.time.Instant;

public record PlatformAuthenticatedSession(
    PlatformPrincipal principal,
    String rawToken,
    Instant expiresAt
) {
}
