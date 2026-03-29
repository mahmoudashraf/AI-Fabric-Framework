package com.ai.fabric.platform.backend.security;

public enum PlatformRole {
    PLATFORM_ADMIN,
    PLATFORM_OPERATOR;

    public String authority() {
        return "ROLE_" + name();
    }
}
