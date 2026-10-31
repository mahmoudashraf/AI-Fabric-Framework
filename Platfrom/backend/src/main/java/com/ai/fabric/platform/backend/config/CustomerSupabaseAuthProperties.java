package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;
import java.net.URI;

@ConfigurationProperties("platform.auth.customer-supabase")
public record CustomerSupabaseAuthProperties(
    boolean enabled, String projectUrl, String publicKey, String issuer, String jwksUri,
    String audience, List<DeskClient> deskClients, List<String> linkOrigins, String deskSignInUrl
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public CustomerSupabaseAuthProperties {
        audience = audience == null || audience.isBlank() ? "authenticated" : audience;
        deskClients = deskClients == null ? List.of() : List.copyOf(deskClients);
        linkOrigins = linkOrigins == null ? List.of() : List.copyOf(linkOrigins);
        deskSignInUrl = deskSignInUrl == null || deskSignInUrl.isBlank() ? null : deskSignInUrl;
        if (deskSignInUrl != null) {
            URI entry = URI.create(deskSignInUrl);
            boolean secure = "https".equals(entry.getScheme()) || ("http".equals(entry.getScheme())
                && List.of("localhost", "127.0.0.1").contains(entry.getHost()));
            String origin = entry.getScheme() + "://" + entry.getRawAuthority();
            if (!secure || entry.getHost() == null || entry.getUserInfo() != null || entry.getQuery() != null
                || entry.getFragment() != null || !List.of("", "/").contains(entry.getPath())
                || deskClients.stream().flatMap(client -> client.redirectUris().stream())
                    .map(URI::create).noneMatch(callback -> origin.equals(callback.getScheme() + "://" + callback.getRawAuthority()))) {
                throw new IllegalArgumentException("Desk sign-in URL must be the root of a configured Desk callback origin.");
            }
        }
    }

    public CustomerSupabaseAuthProperties(boolean enabled, String projectUrl, String publicKey, String issuer,
        String jwksUri, String audience, List<DeskClient> deskClients, List<String> linkOrigins) {
        this(enabled, projectUrl, publicKey, issuer, jwksUri, audience, deskClients, linkOrigins, null);
    }

    public CustomerSupabaseAuthProperties(boolean enabled, String projectUrl, String publicKey, String issuer,
        String jwksUri, String audience, List<DeskClient> deskClients) {
        this(enabled, projectUrl, publicKey, issuer, jwksUri, audience, deskClients, List.of());
    }

    public record DeskClient(String clientId, List<String> redirectUris) {
        public DeskClient { redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris); }
    }
}
