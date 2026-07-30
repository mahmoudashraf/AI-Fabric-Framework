package com.ai.fabric.platform.backend.security;

public enum PlatformRole {
    PLATFORM_ADMIN,
    PLATFORM_OPERATOR,
    PLATFORM_PRODUCT_SERVICE,
    CUSTOMER_ADMIN,
    PUBLIC_API_CLIENT,
    CONSUMER_RUNTIME_ASSIGNMENT_CLIENT,
    PARTNER_AUTHENTICATED,
    PARTNER_ADMIN,
    PARTNER_IMPLEMENTER,
    PARTNER_DEVELOPER,
    PARTNER_SUPPORT;

    public String authority() {
        return "ROLE_" + name();
    }
}
