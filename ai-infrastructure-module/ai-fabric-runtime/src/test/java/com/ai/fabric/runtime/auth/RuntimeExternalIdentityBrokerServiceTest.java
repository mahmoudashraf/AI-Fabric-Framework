package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeExternalIdentityBrokerServiceTest {

    @Test
    void validatesEs256ProviderSignaturesAgainstTheJwkSetAndRejectsTampering() throws Exception {
        var key = new ECKeyGenerator(Curve.P_256).keyID("provider-key").generate();
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        server.start();
        try {
            JwtDecoder decoder = RuntimeExternalIdentityBrokerService.createJwkDecoder(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(),
                new JWTClaimsSet.Builder().subject("verified-user").issuer("https://provider.example/auth/v1")
                    .audience("authenticated").issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(60))).build());
            jwt.sign(new ECDSASigner(key));
            assertThat(decoder.decode(jwt.serialize()).getSubject()).isEqualTo("verified-user");

            String[] parts = jwt.serialize().split("\\.");
            char replacement = parts[2].charAt(0) == 'A' ? 'B' : 'A';
            String tampered = parts[0] + "." + parts[1] + "." + replacement + parts[2].substring(1);
            assertThatThrownBy(() -> decoder.decode(tampered))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void exchangesVerifiedExternalIdentityForDeploymentScopedRuntimeToken() {
        RuntimeAuthProperties properties = properties();
        RuntimePublicTokenService tokenService = new RuntimePublicTokenService(properties);
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("external-token")).thenReturn(jwt(false));
        HttpServletRequest request = request("https://app.customer.example");
        RuntimeExternalIdentityBrokerService service =
            new RuntimeExternalIdentityBrokerService(properties, tokenService, decoder);

        var brokered = service.authenticate(request, "Bearer external-token");
        RuntimeAuthContext context = tokenService.validateBearerToken("Bearer " + brokered.issued().token());

        assertThat(context.getSubjectId()).isEqualTo("customer-user-1");
        assertThat(context.getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER);
        assertThat(context.getAuthMode()).isEqualTo(RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED);
        assertThat(context.getDeploymentId()).isEqualTo("dep-customer");
        assertThat(context.getCustomerId()).isEqualTo("customer-1");
        assertThat(context.getTenantId()).isEqualTo("tenant-1");
        assertThat(context.getGrantedScopes()).containsExactly("chat:query", "chat:conversations");
        assertThat(brokered.email()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsUnapprovedOriginsBeforeDecodingIdentity() {
        RuntimeAuthProperties properties = properties();
        JwtDecoder decoder = mock(JwtDecoder.class);
        RuntimeExternalIdentityBrokerService service = new RuntimeExternalIdentityBrokerService(
            properties, new RuntimePublicTokenService(properties), decoder);

        assertThatThrownBy(() -> service.authenticate(
            request("https://attacker.example"), "Bearer external-token"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(decoder, never()).decode("external-token");
    }

    @Test
    void rejectsProviderAnonymousIdentity() {
        RuntimeAuthProperties properties = properties();
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("external-token")).thenReturn(jwt(true));
        RuntimeExternalIdentityBrokerService service = new RuntimeExternalIdentityBrokerService(
            properties, new RuntimePublicTokenService(properties), decoder);

        assertThatThrownBy(() -> service.authenticate(
            request("https://app.customer.example"), "Bearer external-token"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private RuntimeAuthProperties properties() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getPublicTokens().setSigningKey("runtime-public-token-test-signing-key-with-more-than-thirty-two-bytes");
        properties.getPublicTokens().setIssuer("runtime-public-bootstrap");
        properties.getPublicTokens().setAcceptedIssuers(List.of("runtime-public-bootstrap"));
        properties.getPublicTokens().setDefaultAudience("dep-customer");
        properties.getPublicTokens().setAcceptedAudiences(List.of("dep-customer"));
        properties.getPublicTokens().setAuthenticatedAllowedScopes(List.of(
            "chat:query", "chat:suggestions", "chat:conversations"));
        properties.getPublicTokens().getDefaults().setDeploymentId("dep-customer");
        properties.getPublicTokens().getDefaults().setCustomerId("customer-1");
        properties.getPublicTokens().getDefaults().setTenantId("tenant-1");
        RuntimeAuthProperties.ExternalIdentityBroker broker = properties.getExternalIdentityBroker();
        broker.setEnabled(true);
        broker.setIssuer("https://example.supabase.co/auth/v1");
        broker.setJwkSetUri("https://example.supabase.co/auth/v1/.well-known/jwks.json");
        broker.setAudience("authenticated");
        broker.setRequireVerifiedEmail(true);
        broker.setAllowedOrigins(List.of("https://app.customer.example"));
        broker.setGrantedScopes(List.of("chat:query", "chat:conversations"));
        return properties;
    }

    private HttpServletRequest request(String origin) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn(origin);
        when(request.getRemoteAddr()).thenReturn("192.0.2.5");
        return request;
    }

    private Jwt jwt(boolean anonymous) {
        return Jwt.withTokenValue("external-token")
            .header("alg", "RS256")
            .subject("customer-user-1")
            .claim("session_id", "provider-session-1")
            .claim("email", "user@example.com")
            .claim("email_verified", true)
            .claim("is_anonymous", anonymous)
            .issuedAt(Instant.now().minusSeconds(10))
            .expiresAt(Instant.now().plusSeconds(280))
            .build();
    }
}
