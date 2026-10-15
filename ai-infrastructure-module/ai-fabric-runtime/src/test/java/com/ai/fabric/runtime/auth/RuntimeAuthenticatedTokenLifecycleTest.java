package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeAuthenticatedTokenLifecycleTest {
    private final MutableClock clock = new MutableClock();
    private RuntimeAuthProperties properties() {
        var properties = new RuntimeAuthProperties();
        var token = properties.getPublicTokens();
        token.setSigningKey("test-only-deployment-key"); token.setIssuer("target-one");
        token.setAcceptedIssuers(List.of("target-one")); token.setDefaultAudience("target-one");
        token.setAcceptedAudiences(List.of("target-one"));
        token.getDefaults().setDeploymentId("deployment-one"); token.getDefaults().setCustomerId("customer-one"); token.getDefaults().setTenantId("tenant-one");
        var broker = properties.getExternalIdentityBroker(); broker.setEnabled(true);
        broker.setAllowedOrigins(List.of("https://app.example.test"));
        return properties;
    }
    private RuntimePublicTokenService.IssuedPublicRuntimeToken issue(RuntimePublicTokenService service, String user) {
        return service.issueAuthenticatedToken(user, RuntimeAuthSubjectType.END_USER, "session-" + user,
            "deployment-one", "customer-one", "tenant-one", List.of("chat:query"), null, List.of());
    }
    @Test void issuedTokenSurvivesClientLogoutOnlyUntilItsExactFiveMinuteExpiry() {
        var service = new RuntimePublicTokenService(properties(), clock);
        var issued = issue(service, "user-one");
        assertThat(issued.authContext().getExpiresAt()).isEqualTo(clock.now.plusSeconds(300));
        // There is no server revocation ledger: a copied token is honestly valid before exp.
        clock.now = clock.now.plusSeconds(299);
        assertThat(service.validateBearerToken("Bearer " + issued.token()).getSubjectId()).isEqualTo("user-one");
        clock.now = clock.now.plusSeconds(1);
        assertThatThrownBy(() -> service.validateBearerToken("Bearer " + issued.token())).hasMessageContaining("expired");
    }
    @Test void refreshingSameSubjectPreservesOwnershipWhileAccountSwitchDoesNotTransferIt() {
        var service = new RuntimePublicTokenService(properties(), clock);
        var old = issue(service, "user-one"); clock.now = clock.now.plusSeconds(250);
        var refreshed = issue(service, "user-one"); var other = issue(service, "user-two");
        assertThat(refreshed.authContext().getSessionId()).isEqualTo(old.authContext().getSessionId());
        assertThat(other.authContext().getSubjectId()).isNotEqualTo(old.authContext().getSubjectId());
        clock.now = clock.now.plusSeconds(50);
        assertThatThrownBy(() -> service.validateBearerToken("Bearer " + old.token())).hasMessageContaining("expired");
        assertThat(service.validateBearerToken("Bearer " + refreshed.token()).getSubjectId()).isEqualTo("user-one");
    }
    @Test void tokensCannotCrossDeploymentSigningAuthority() {
        var original = new RuntimePublicTokenService(properties(), clock);
        var other = properties(); other.getPublicTokens().setSigningKey("another-deployment-key");
        assertThatThrownBy(() -> new RuntimePublicTokenService(other, clock).validateBearerToken("Bearer " + issue(original, "user-one").token()))
            .hasMessageContaining("signature");
    }
    @Test void runtimeExpiryCannotExtendProviderExpiryEvenAfterAnotherExchange() {
        var properties = properties(); var service = new RuntimePublicTokenService(properties, clock);
        var decoder = mock(JwtDecoder.class);
        var providerExpiry = clock.now.plusSeconds(40);
        when(decoder.decode("provider-token")).thenReturn(Jwt.withTokenValue("provider-token").header("alg", "RS256")
            .subject("user-one").claim("session_id", "provider-session").issuedAt(clock.now.minusSeconds(10)).expiresAt(providerExpiry).build());
        var request = mock(HttpServletRequest.class); when(request.getHeader("Origin")).thenReturn("https://app.example.test");
        var broker = new RuntimeExternalIdentityBrokerService(properties, service, decoder, clock);
        var token = broker.authenticate(request, "Bearer provider-token").issued();
        assertThat(token.authContext().getExpiresAt()).isEqualTo(providerExpiry);
        clock.now = clock.now.plusSeconds(30);
        assertThat(broker.authenticate(request, "Bearer provider-token").issued().authContext().getExpiresAt()).isEqualTo(providerExpiry);
        clock.now = providerExpiry;
        assertThatThrownBy(() -> service.validateBearerToken("Bearer " + token.token())).hasMessageContaining("expired");
        assertThatThrownBy(() -> broker.authenticate(request, "Bearer provider-token")).hasMessageContaining("lifetime is invalid");
    }
    @Test void acceptsNormalProviderLifetimeButBoundsRuntimeTokenAndRejectsInvalidRuntimeConfiguration() {
        var properties = properties(); var service = new RuntimePublicTokenService(properties, clock); var decoder = mock(JwtDecoder.class);
        when(decoder.decode("provider-token")).thenReturn(Jwt.withTokenValue("provider-token").header("alg", "RS256")
            .subject("user-one").issuedAt(clock.now.minusSeconds(1800)).expiresAt(clock.now.plusSeconds(3600)).build());
        var request = mock(HttpServletRequest.class); when(request.getHeader("Origin")).thenReturn("https://app.example.test");
        var issued = new RuntimeExternalIdentityBrokerService(properties, service, decoder, clock)
            .authenticate(request, "Bearer provider-token").issued();
        assertThat(issued.authContext().getExpiresAt()).isEqualTo(clock.now.plusSeconds(300));
        properties.getPublicTokens().setAuthenticatedTtlSeconds(301);
        assertThatThrownBy(() -> issue(service, "user-one")).hasMessageContaining("300 seconds");
    }
    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
