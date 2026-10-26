package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties("platform.auth.customer-supabase")
public record CustomerSupabaseAuthProperties(
    boolean enabled, String projectUrl, String publicKey, String issuer, String jwksUri,
    String audience, List<DeskClient> deskClients, List<String> linkOrigins
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public CustomerSupabaseAuthProperties {
        audience = audience == null || audience.isBlank() ? "authenticated" : audience;
        deskClients = deskClients == null ? List.of() : List.copyOf(deskClients);
        linkOrigins = linkOrigins == null ? List.of() : List.copyOf(linkOrigins);
    }

    public CustomerSupabaseAuthProperties(boolean enabled, String projectUrl, String publicKey, String issuer,
        String jwksUri, String audience, List<DeskClient> deskClients) {
        this(enabled, projectUrl, publicKey, issuer, jwksUri, audience, deskClients, List.of());
    }

    public record DeskClient(String clientId, List<String> redirectUris) {
        public DeskClient { redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris); }
    }
}
