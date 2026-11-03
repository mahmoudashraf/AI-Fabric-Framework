package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import static com.ai.fabric.platform.backend.support.SupportContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupportRequesterAssertionServiceTest {
    @Test void hostAssertionHasBoundedIdentityAndOptionalExplicitConversationProofNotEmail() throws Exception {
        var settings = mock(SupportSettings.class);
        when(settings.deskBindingId()).thenReturn("desk-space");
        when(settings.binding()).thenReturn(new RuntimeBinding("owner", "tenant", "dep", "target", "issuer", "audience"));
        var secrets = mock(PlatformSecretService.class);
        Map<String,String> values = new HashMap<>();
        when(secrets.resolveSecret(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(secrets.upsertManagedSecret(anyString(), anyString(), anyMap())).thenAnswer(call -> {
            values.put(call.getArgument(0),call.getArgument(1)); return null;
        });
        var service = new SupportRequesterAssertionService(settings, secrets,
            new MockEnvironment().withProperty("LOOMAI_DESK_REVIEW_BRIDGE_BASE_URL", "https://api.example.test"));
        service.prepare();
        var keysBefore = Map.copyOf(values);
        service.prepare();
        assertThat(values).isEqualTo(keysBefore);
        assertThat(service.serverKey()).isNotEqualTo(service.connectorKey());
        var owner = new Requester("customer-a","alice", "https://identity.example.test", "immutable-alice");
        var jwt = SignedJWT.parse(service.issue(owner, "desk-space", "support-conversation:opaque", "chat-opaque"));
        var publicKey = (RSAKey) JWKSet.parse(service.publicKeys()).getKeys().getFirst();
        assertThat(publicKey.isPrivate()).isFalse();
        assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
        var claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("https://api.example.test/api/support/requester");
        assertThat(claims.getAudience()).containsExactly("loomai-support-requester:desk-space");
        assertThat(claims.getSubject()).isEqualTo(SupportRequesterAssertionService.subject(owner));
        assertThat(claims.getSubject()).doesNotContain("alice", "@");
        assertThat(claims.getStringClaim("runtimePrincipal")).isEqualTo("support-conversation:opaque");
        assertThat(claims.getStringClaim("runtimeConversationId")).isEqualTo("chat-opaque");
        assertThat(claims.getExpirationTime().toInstant()).isBetween(Instant.now().plusSeconds(100), Instant.now().plusSeconds(121));
        assertThat(SignedJWT.parse(service.issue(owner,"desk-space")).getJWTClaimsSet().getClaim("runtimePrincipal")).isNull();
        var other = new Requester("customer-b","alice",owner.issuer(),owner.subject());
        assertThat(SupportRequesterAssertionService.subject(other)).isNotEqualTo(claims.getSubject());
        assertThat(service.publicKeys().toString()).doesNotContain(service.serverKey(), service.connectorKey());
    }
}
