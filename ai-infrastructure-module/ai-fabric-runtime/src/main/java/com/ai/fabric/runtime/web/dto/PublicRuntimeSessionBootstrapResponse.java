package com.ai.fabric.runtime.web.dto;

public record PublicRuntimeSessionBootstrapResponse(
    boolean success,
    String tokenType,
    String token,
    String authMode,
    String subjectType,
    String sessionId,
    String expiresAt
) {
}
