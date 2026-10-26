package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseUserVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.*;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.JwtException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CustomerSupabaseConfigurationTest {
    private static HttpServer server;
    private static RSAKey rsa;
    private static ECKey ec;
    private static String base;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll static void serveKeys() throws Exception {
        rsa = new RSAKeyGenerator(2048).keyID("rsa-test").generate();
        ec = new ECKeyGenerator(Curve.P_256).keyID("ec-test").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/.well-known/jwks.json", exchange -> {
            byte[] body = new JWKSet(List.of(rsa.toPublicJWK(), ec.toPublicJWK())).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/auth/v1/user", exchange -> {
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            int status = "Bearer revoked".equals(token) ? 401 : "Bearer unavailable".equals(token) ? 503 : 200;
            String response = "Bearer metadata-only".equals(token)
                ? "{\"id\":\"subject\",\"user_metadata\":{\"email_verified\":true}}"
                : "Bearer wrong-subject".equals(token) ? "{\"id\":\"foreign\",\"email_confirmed_at\":\"2026-01-01T00:00:00Z\"}"
                : "{\"id\":\"subject\",\"email_confirmed_at\":\"2026-01-01T00:00:00Z\"}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start(); base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterAll static void stop() { if (server != null) server.stop(0); }

    @Test void validatesBothSupportedAsymmetricAlgorithms() throws Exception {
        var decoder = new CustomerSupabaseConfiguration().customerSupabaseJwtDecoder(properties(), mapper);
        assertThat(decoder.decode(token(claims(), false)).getSubject()).isEqualTo("subject");
        assertThat(decoder.decode(token(claims(), true)).getSubject()).isEqualTo("subject");
    }

    @Test void rejectsWrongIssuerAudienceExpiredMissingExpiryServiceAndAnonymousTokens() throws Exception {
        var decoder = new CustomerSupabaseConfiguration().customerSupabaseJwtDecoder(properties(), mapper);
        for (var claims : List.of(claims().issuer("https://other.test/auth/v1"), claims().audience("wrong"),
            claims().expirationTime(Date.from(Instant.now().minusSeconds(180))), claims().expirationTime(null),
            claims().claim("role", "service_role"), claims().claim("role", "anon"), claims().claim("is_anonymous", true),
            claims().claim("client_id", "third-party-or-desk-client"),
            claims().claim("session_id", null), claims().subject(null))) {
            String token = token(claims, false);
            assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
        }
    }

    @Test void rejectsUntrustedSigningKey() throws Exception {
        var foreign = new RSAKeyGenerator(2048).keyID("foreign").generate();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("foreign").build(), claims().build());
        jwt.sign(new RSASSASigner(foreign));
        var decoder = new CustomerSupabaseConfiguration().customerSupabaseJwtDecoder(properties(), mapper);
        assertThatThrownBy(() -> decoder.decode(jwt.serialize())).isInstanceOf(JwtException.class);
    }

    @Test void sharedIdentityVerifierAcceptsOnlyRegisteredDeskClientsForDelegation() throws Exception {
        var p = properties();
        var configured = new CustomerSupabaseAuthProperties(true, p.projectUrl(), p.publicKey(), p.issuer(), p.jwksUri(),
            p.audience(), List.of(new CustomerSupabaseAuthProperties.DeskClient("desk-approved", List.of("https://desk.example.test/auth/callback"))));
        var decoder = new CustomerSupabaseConfiguration().customerSupabaseJwtDecoder(configured, mapper);
        assertThat(decoder.decode(token(claims().claim("client_id", "desk-approved"), false)).getSubject()).isEqualTo("subject");
        String unknown = token(claims().claim("client_id", "foreign-client"), false);
        assertThatThrownBy(() -> decoder.decode(unknown)).isInstanceOf(JwtException.class);
    }

    @Test void rejectsPublishingSecretKeysOrJwksForAnotherProject() {
        var p = properties();
        assertThatThrownBy(() -> new CustomerSupabaseConfiguration().customerSupabaseJwtDecoder(new CustomerSupabaseAuthProperties(
            true, p.projectUrl(), "sb_secret_do_not_publish", p.issuer(), p.jwksUri(), p.audience(), p.deskClients()), mapper))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new CustomerSupabaseConfiguration().customerSupabaseJwtDecoder(new CustomerSupabaseAuthProperties(
            true, p.projectUrl(), p.publicKey(), p.issuer(), "https://foreign.test/keys", p.audience(), p.deskClients()), mapper))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void emailVerificationComesFromProviderNotUserEditableMetadata() {
        var verifier = new CustomerSupabaseUserVerifier(properties(), mapper);
        verifier.requireVerifiedUser("verified", "subject");
        for (String token : List.of("metadata-only", "wrong-subject", "revoked", "unavailable")) {
            assertThatThrownBy(() -> verifier.requireVerifiedUser(token, "subject"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        }
    }

    private CustomerSupabaseAuthProperties properties() {
        return new CustomerSupabaseAuthProperties(true, base, "sb_publishable_test_public_key", base + "/auth/v1",
            base + "/auth/v1/.well-known/jwks.json", "authenticated", List.of());
    }
    private JWTClaimsSet.Builder claims() {
        return new JWTClaimsSet.Builder().issuer(base + "/auth/v1").subject("subject").audience("authenticated")
            .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("session_id", UUID.randomUUID().toString()).claim("role", "authenticated");
    }
    private String token(JWTClaimsSet.Builder claims, boolean elliptic) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(elliptic ? JWSAlgorithm.ES256 : JWSAlgorithm.RS256)
            .keyID(elliptic ? ec.getKeyID() : rsa.getKeyID()).build(), claims.build());
        jwt.sign(elliptic ? new ECDSASigner(ec) : new RSASSASigner(rsa));
        return jwt.serialize();
    }
}
