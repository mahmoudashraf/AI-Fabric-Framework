package com.ai.fabric.platform.backend.partner.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PartnerSecurityContext {

    private PartnerSecurityContext() {
    }

    public static PartnerPrincipal currentPrincipalOrThrow() {
        PartnerPrincipal principal = currentPrincipal();
        if (principal == null) {
            throw new PartnerUnauthorizedException("Partner authentication is required.");
        }
        return principal;
    }

    public static PartnerPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof PartnerPrincipal partnerPrincipal ? partnerPrincipal : null;
    }
}
