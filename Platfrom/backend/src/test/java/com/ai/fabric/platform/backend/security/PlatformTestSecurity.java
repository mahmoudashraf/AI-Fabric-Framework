package com.ai.fabric.platform.backend.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class PlatformTestSecurity {

    private PlatformTestSecurity() {
    }

    public static void authenticateAsPlatformAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
            PlatformAuthenticationSupport.authenticationFor(
                new PlatformPrincipal(
                    "test-admin@example.com",
                    PlatformRole.PLATFORM_ADMIN,
                    "Test Platform Admin",
                    "TEST"
                )
            )
        );
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
