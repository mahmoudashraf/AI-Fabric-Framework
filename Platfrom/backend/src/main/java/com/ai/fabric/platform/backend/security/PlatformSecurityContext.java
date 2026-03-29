package com.ai.fabric.platform.backend.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PlatformSecurityContext {

    private PlatformSecurityContext() {
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)
            && authentication.getPrincipal() instanceof PlatformPrincipal;
    }

    public static String actorIdOrSystem() {
        return currentPrincipal() == null ? "system" : currentPrincipal().actorId();
    }

    public static String actorRoleOrSystem() {
        return currentPrincipal() == null ? "SYSTEM" : currentPrincipal().role().name();
    }

    public static PlatformPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof PlatformPrincipal platformPrincipal ? platformPrincipal : null;
    }
}
