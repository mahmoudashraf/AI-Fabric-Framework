package com.ai.fabric.platform.backend.security;

public enum PlatformRole {
    PLATFORM_ADMIN,
    PLATFORM_OPERATOR,
    CUSTOMER_ADMIN,
    PUBLIC_API_CLIENT;

    public String authority() {
        return "ROLE_" + name();
    }
}
