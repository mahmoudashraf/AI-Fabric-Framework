package com.ai.fabric.platform.backend.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

final class PlatformAuthenticationSupport {

    private PlatformAuthenticationSupport() {
    }

    static UsernamePasswordAuthenticationToken authenticationFor(PlatformPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
    }
}
