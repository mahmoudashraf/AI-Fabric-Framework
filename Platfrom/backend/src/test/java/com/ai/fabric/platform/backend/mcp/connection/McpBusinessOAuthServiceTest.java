package com.ai.fabric.platform.backend.mcp.connection;

import static com.ai.fabric.platform.backend.mcp.connection.McpConnectionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.oauth2.sdk.id.State;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class McpBusinessOAuthServiceTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final McpConnectionRepository connections = mock(McpConnectionRepository.class);
    private final McpOAuthAttemptRepository attempts = mock(McpOAuthAttemptRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final PlatformUserRepository users = mock(PlatformUserRepository.class);
    private final McpOwnedSecrets secrets = mock(McpOwnedSecrets.class);
    private final McpBusinessOAuthClient client = mock(McpBusinessOAuthClient.class);
    private final McpConnectionProperties properties = new McpConnectionProperties();
    private final McpCredentialCrypto crypto = new McpCredentialCrypto(properties);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final McpBusinessOAuthService service = new McpBusinessOAuthService(connections, attempts, deployments, access,
        users, secrets, client, crypto, properties, mapper, audit);
    private final McpConnectionEntity connection = new McpConnectionEntity();
    private final PlatformUserEntity user = new PlatformUserEntity();
    private final DeploymentEntity brain = new DeploymentEntity();
    private final Map<String, McpOAuthAttemptEntity> attemptStore = new HashMap<>();
    private static final String BROWSER = "this-browser-binding-is-not-a-provider-credential";
    private String rawState;

    @BeforeEach void setup() throws Exception {
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        properties.setCallbackUrl("https://api.loomai.example/api/mcp/oauth/callback");
        user.setId("user-owner"); user.setEmail("owner@example.test"); user.setStatus("ACTIVE"); user.setRole("CUSTOMER_ADMIN"); user.setDisplayName("Owner");
        signIn(user.getId()); when(users.findById(user.getId())).thenReturn(Optional.of(user));
        brain.setId("dep-one"); brain.setCustomerId("company-one"); brain.setStatus("DRAFT");
        when(deployments.findById(brain.getId())).thenReturn(Optional.of(brain));
        when(access.requireDeploymentEditorAccess(brain)).thenReturn(brain);
        connection.setId("mcp-one"); connection.setDeploymentId(brain.getId()); connection.setCustomerId(brain.getCustomerId());
        connection.setTargetProfileId("stage"); connection.setConnectionModel("BUSINESS"); connection.setRevision(1);
        connection.setEndpointUrl("https://company.example/mcp"); connection.setAuthStatus("AUTHORIZATION_REQUIRED"); connection.setStatus("ACTIVE");
        connection.setAuthConfigJson(mapper.writeValueAsString(new AuthSettings("OAUTH_AUTHORIZATION_CODE", null, null, List.of(), settings())));
        when(connections.findLocked(connection.getId())).thenReturn(Optional.of(connection));
        when(attempts.save(any())).thenAnswer(a -> { var v = (McpOAuthAttemptEntity) a.getArgument(0); attemptStore.put(v.getStateHash(), v); return v; });
        when(attempts.findLocked(anyString())).thenAnswer(a -> Optional.ofNullable(attemptStore.get(a.getArgument(0))));
        when(client.discover(anyString(), any(), eq(false))).thenReturn(mapper.readTree("{\"issuer\":\"https://auth.example\",\"resource\":\"https://company.example/mcp\",\"token_endpoint\":\"https://auth.example/token\"}"));
        when(client.register(any(), any(), any())).thenReturn(new McpBusinessOAuthClient.Registration("registered-client", "registered-secret", "client_secret_basic"));
        when(client.authorizationUrl(any(), anyList(), anyString(), any(), any(), any())).thenAnswer(a -> {
            rawState = ((State) a.getArgument(4)).getValue(); return "https://auth.example/authorize?state=" + rawState;
        });
        when(client.token(any(), anyString(), nullable(String.class), anyString(), any(), anyList()))
            .thenReturn(new McpBusinessOAuthClient.TokenResult("private-access", "private-refresh", Instant.now().plusSeconds(60)));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void ownerFlowUsesEncryptedPkceSingleUseStateAndOneConnectionGrant() {
        var started = begin(); var attempt = attemptStore.values().iterator().next();
        assertThat(attempt.getStateHash()).isNotEqualTo(rawState); assertThat(attempt.getActorId()).isEqualTo(user.getId());
        assertThat(attempt.getEncryptedVerifier()).doesNotContain("registered-secret", "verifier");
        assertThat(connection.getEncryptedTokens()).isNull();
        var result = complete();
        assertThat(result.outcome()).isEqualTo("connected"); assertThat(result.returnPath()).startsWith("/smart-brains/dep-one/capabilities");
        assertThat(connection.getAuthStatus()).isEqualTo("CONNECTED");
        assertThat(connection.getRevision()).isEqualTo(1);
        assertThat(connection.getEncryptedTokens()).doesNotContain("private-access", "private-refresh", "registered-secret");
        assertThat(attempt.getConsumedAt()).isNotNull(); assertThat(attempt.getEncryptedVerifier()).isNull();
        assertThatThrownBy(this::complete).isInstanceOf(ResponseStatusException.class);
        verify(client, times(1)).token(any(), anyString(), nullable(String.class), anyString(), any(), anyList());
    }

    @Test void missingWrongAndExpiredBrowserStateNeverAcquireGrant() {
        begin();
        assertThatThrownBy(() -> service.callback(rawState, "code", null, null, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.callback(rawState, "code", null, null, "wrong-browser")).isInstanceOf(ResponseStatusException.class);
        attemptStore.values().iterator().next().setExpiresAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(this::complete).isInstanceOf(ResponseStatusException.class);
        verify(client, never()).token(any(), anyString(), nullable(String.class), anyString(), any(), anyList());
        assertThat(connection.getEncryptedTokens()).isNull();
    }

    @Test void actorRevocationDifferentLoggedInActorAndConfigurationChangePreventBinding() {
        begin(); user.setStatus("DISABLED"); assertThat(complete().outcome()).isEqualTo("failed");
        user.setStatus("ACTIVE"); begin();
        var other = new PlatformUserEntity(); other.setId("other-user"); when(users.findById("other-user")).thenReturn(Optional.of(other));
        signIn("other-user"); assertThat(complete().outcome()).isEqualTo("failed");
        signIn(user.getId()); begin(); connection.setRevision(2); assertThat(complete().outcome()).isEqualTo("failed");
        verify(client, never()).token(any(), anyString(), nullable(String.class), anyString(), any(), anyList());
    }

    @Test void callbackWithoutConsoleBearerRechecksStoredActorAndCurrentDelegation() {
        begin(); SecurityContextHolder.clearContext();
        assertThat(complete().outcome()).isEqualTo("connected");
        assertThat(PlatformSecurityContext.currentPrincipal()).isNull();
        verify(access, times(2)).requireDeploymentEditorAccess(brain);
    }

    @Test void cancelledConsentAndIssuerMismatchGrantNothingAndConsumeAttempt() {
        begin(); var cancelled = service.callback(rawState, null, "access_denied", null, BROWSER);
        assertThat(cancelled.outcome()).isEqualTo("cancelled");
        begin(); var failed = service.callback(rawState, "code", null, "https://different.example", BROWSER);
        assertThat(failed.outcome()).isEqualTo("failed"); assertThat(connection.getEncryptedTokens()).isNull();
        verify(client, never()).token(any(), anyString(), nullable(String.class), anyString(), any(), anyList());
    }

    @Test void tokenRefreshPersistsRotatedGrantWithoutChangingConfigurationRevision() {
        begin(); complete(); connection.setTokenExpiresAt(Instant.now().minusSeconds(1));
        when(client.token(any(), anyString(), nullable(String.class), anyString(), any(), anyList()))
            .thenReturn(new McpBusinessOAuthClient.TokenResult("rotated-access", "rotated-refresh", Instant.now().plusSeconds(300)));
        assertThat(service.accessToken(connection)).isEqualTo("rotated-access"); assertThat(connection.getRevision()).isEqualTo(1);
        String clear = crypto.decrypt("connection:" + connection.getId(), connection.getEncryptedTokens());
        assertThat(clear).contains("rotated-refresh").doesNotContain("private-refresh");
        assertThat(service.accessToken(connection)).isEqualTo("rotated-access");
        verify(client, times(2)).token(any(), anyString(), nullable(String.class), anyString(), any(), anyList());
    }

    @Test void rejectedRefreshRequiresReconnectWithoutRetryOrCredentialSwitch() {
        begin(); complete(); connection.setTokenExpiresAt(Instant.now().minusSeconds(1));
        when(client.token(any(), anyString(), nullable(String.class), anyString(), any(), anyList())).thenThrow(new McpBusinessOAuthClient.GrantRejected());
        assertThatThrownBy(() -> service.accessToken(connection)).isInstanceOf(McpBusinessOAuthClient.GrantRejected.class);
        assertThat(connection.getAuthStatus()).isEqualTo("RECONNECT_REQUIRED");
        assertThat(crypto.decrypt("connection:" + connection.getId(), connection.getEncryptedTokens())).doesNotContain("private-access", "private-refresh");
    }

    @Test void localDisconnectRemovesGrantEvenWhenProviderRevocationFails() {
        begin(); complete(); doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY)).when(client)
            .revoke(any(), anyString(), nullable(String.class), anyString(), anyString(), anyBoolean());
        service.disconnect(connection);
        assertThat(connection.getEncryptedTokens()).isNull(); assertThat(connection.getTokenExpiresAt()).isNull();
        assertThat(connection.getAuthStatus()).isEqualTo("DISCONNECTED_UPSTREAM_REVOCATION_FAILED");
    }

    @Test void publicAndPersonalModelsCannotStartOAuthAndReturnPathsAreBrainScoped() throws Exception {
        connection.setConnectionModel("PUBLIC"); assertThatThrownBy(this::begin).isInstanceOf(ResponseStatusException.class);
        connection.setConnectionModel("BUSINESS");
        assertThatThrownBy(() -> service.authorize("dep-one", "mcp-one", new Authorize(1L, "https://evil.example"), BROWSER)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.authorize("dep-one", "mcp-one", new Authorize(1L, "/smart-brains/other/capabilities"), BROWSER)).isInstanceOf(ResponseStatusException.class);
        verify(client, never()).discover(anyString(), any(), anyBoolean());
    }

    private AuthorizationStart begin() { return service.authorize("dep-one", "mcp-one", new Authorize(1L, "/smart-brains/dep-one/capabilities?targetProfileId=stage&view=mcp"), BROWSER); }
    private CallbackResult complete() { return service.callback(rawState, "provider-code", null, "https://auth.example", BROWSER); }
    private static OAuthSettings settings() { return new OAuthSettings(null, null, "PREREGISTERED", "registered-client", null, "none", List.of("read")); }
    private static void signIn(String id) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new PlatformPrincipal("owner@example.test", PlatformRole.CUSTOMER_ADMIN, "Owner", "LOCAL", id), null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER_ADMIN")))); }
}
