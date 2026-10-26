package com.ai.fabric.platform.backend.security;

public final class CustomerSessionSupport {
    private CustomerSessionSupport() { }

    public static boolean isCustomerSession(PlatformPrincipal principal) {
        return principal != null && principal.role() == PlatformRole.CUSTOMER_ADMIN
            && ("SESSION".equals(principal.authenticationMode()) || ("SUPABASE".equals(principal.authenticationMode())
                && principal.platformUserId() != null && !principal.platformUserId().isBlank()));
    }
}
