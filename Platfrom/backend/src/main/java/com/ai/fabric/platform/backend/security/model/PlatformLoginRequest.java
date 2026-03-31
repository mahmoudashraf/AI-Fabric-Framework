package com.ai.fabric.platform.backend.security.model;

public record PlatformLoginRequest(
    String email,
    String password
) {
}
