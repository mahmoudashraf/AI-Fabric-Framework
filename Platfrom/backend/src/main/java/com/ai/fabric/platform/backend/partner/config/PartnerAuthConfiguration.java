package com.ai.fabric.platform.backend.partner.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration
public class PartnerAuthConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "platform.auth.supabase", name = "enabled", havingValue = "true")
    public JwtDecoder partnerSupabaseJwtDecoder(PartnerSupabaseAuthProperties properties) {
        if (!StringUtils.hasText(properties.jwksUri())) {
            throw new IllegalStateException("platform.auth.supabase.jwks-uri is required when Supabase partner auth is enabled.");
        }
        return NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build();
    }
}
