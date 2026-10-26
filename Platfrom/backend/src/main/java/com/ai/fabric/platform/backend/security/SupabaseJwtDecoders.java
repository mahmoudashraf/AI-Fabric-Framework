package com.ai.fabric.platform.backend.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class SupabaseJwtDecoders {
    private SupabaseJwtDecoders() { }

    public static NimbusJwtDecoder fromJwks(String jwksUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build();
    }
}
