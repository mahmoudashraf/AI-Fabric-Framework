package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimePublicTokenSigningServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private RuntimePublicTokenSigningService service() {
        var secrets = mock(PlatformSecretService.class);
        when(secrets.resolveSecret(RuntimePublicTokenSigningService.SECRET_NAME)).thenReturn("test-only-signing-key");
        return new RuntimePublicTokenSigningService(secrets, mapper);
    }
    private RuntimePublicTokenSigningService.RuntimePublicTokenClaims claims(Instant expiresAt) {
        return new RuntimePublicTokenSigningService.RuntimePublicTokenClaims("user", "END_USER", "PUBLIC_RUNTIME_AUTHENTICATED",
            "PUBLIC_BROWSER", "provider-session", "deployment", "customer", "tenant", "issuer", expiresAt, List.of("target"), List.of("chat:query"));
    }
    @Test void platformSignedAuthenticatedTokensCarryAnIssuedAtAndBoundedExpiry() throws Exception {
        var token = service().issueToken(claims(Instant.now().plusSeconds(299)));
        var payload = mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        assertThat(Instant.parse(payload.path("exp").asText()))
            .isBeforeOrEqualTo(Instant.parse(payload.path("iat").asText()).plusSeconds(300));
        assertThat(payload.path("sub").asText()).isEqualTo("user");
    }
    @Test void cannotMintLongLivedOrExpiredAuthenticatedRuntimeCredentials() {
        var signer = service();
        assertThatThrownBy(() -> signer.issueToken(claims(Instant.now().plusSeconds(3600))))
            .hasMessageContaining("at most 300 seconds");
        assertThatThrownBy(() -> signer.issueToken(claims(Instant.now().minusSeconds(1))))
            .hasMessageContaining("positive");
    }
}
