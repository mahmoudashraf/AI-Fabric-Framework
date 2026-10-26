package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import java.net.URI;

@Configuration
@EnableConfigurationProperties(CustomerSupabaseAuthProperties.class)
public class CustomerSupabaseConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "platform.auth.customer-supabase", name = "enabled", havingValue = "true")
    public JwtDecoder customerSupabaseJwtDecoder(CustomerSupabaseAuthProperties properties, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        URI project = URI.create(properties.projectUrl());
        requireSecure(project);
        String key = properties.publicKey();
        boolean publicKey = key != null && key.startsWith("sb_publishable_") && key.length() > 20;
        if (!publicKey && key != null) {
            try {
                String[] parts = key.split("\\.");
                publicKey = parts.length == 3 && "anon".equals(mapper.readTree(
                    java.util.Base64.getUrlDecoder().decode(parts[1])).path("role").asText());
            } catch (Exception ignored) { publicKey = false; }
        }
        if (!publicKey) throw new IllegalStateException("Customer auth public-key must be a Supabase publishable or legacy anon key, never a secret/service key.");
        String base = properties.projectUrl().replaceAll("/+$", "");
        if (!((base + "/auth/v1").equals(properties.issuer()))
            || !((base + "/auth/v1/.well-known/jwks.json").equals(properties.jwksUri()))) {
            throw new IllegalStateException("Customer Supabase issuer and JWKS must belong to its configured project URL.");
        }
        for (var client : properties.deskClients()) {
            if (client.clientId() == null || client.clientId().isBlank() || client.redirectUris().isEmpty()) {
                throw new IllegalStateException("Desk OAuth clients require exact client IDs and callback URLs.");
            }
            for (String redirect : client.redirectUris()) {
                URI uri = URI.create(redirect);
                requireSecure(uri);
                if (uri.getRawQuery() != null || uri.getFragment() != null || redirect.contains("*")) {
                    throw new IllegalStateException("Desk OAuth callback URLs must be exact, without query or fragment.");
                }
            }
        }
        var decoder = SupabaseJwtDecoders.fromJwks(properties.jwksUri());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), jwt -> {
                boolean valid = jwt.getAudience().contains(properties.audience()) && jwt.getExpiresAt() != null
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                    && "authenticated".equals(jwt.getClaimAsString("role"))
                    && (!jwt.hasClaim("client_id") || properties.deskClients().stream()
                        .anyMatch(client -> client.clientId().equals(jwt.getClaimAsString("client_id"))))
                    && jwt.getClaimAsString("session_id") != null && !jwt.getClaimAsString("session_id").isBlank()
                    && !Boolean.TRUE.equals(jwt.getClaim("is_anonymous"));
                return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "A customer Supabase user access token is required.", null));
            }));
        return decoder;
    }

    private void requireSecure(URI uri) {
        boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !local) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalStateException("Supabase and Desk OAuth addresses require HTTPS (localhost excepted).");
        }
    }
}
