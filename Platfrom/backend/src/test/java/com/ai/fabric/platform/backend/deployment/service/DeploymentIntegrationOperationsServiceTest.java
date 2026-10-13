package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentIntegrationOperationsServiceTest {

    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
    private final DeploymentAccessService accessService = mock(DeploymentAccessService.class);
    private final PlatformSecretService secretService = mock(PlatformSecretService.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void overviewUsesReadScopeAndDeploymentTenantClaims() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> trustedKey = new AtomicReference<>();
        AtomicReference<String> assertion = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            trustedKey.set(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER));
            assertion.set(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER));
            respond(exchange, 200, "{\"status\":\"READY\"}");
        });
        configureDeployment();

        var response = service().overview("dep-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(path.get()).isEqualTo("/api/admin/connector/integrations");
        assertThat(trustedKey.get()).isEqualTo("trusted-runtime-key");
        JsonNode claims = assertionClaims(assertion.get());
        assertThat(claims.path("deploymentId").asText()).isEqualTo("dep-1");
        assertThat(claims.path("tenantId").asText()).isEqualTo("tenant-1");
        assertThat(claims.path("aud").asText()).isEqualTo("dep-1");
        assertThat(claims.path("scopes").toString()).isEqualTo("[\"runtime:connector:read\"]");
        verify(accessService).requireDeploymentAccess(any(DeploymentEntity.class));
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reconcileUsesWriteScopeAndAuditsSuccessfulOperatorCommand() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> assertion = new AtomicReference<>();
        startServer(exchange -> {
            method.set(exchange.getRequestMethod());
            assertion.set(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER));
            respond(exchange, 202, "{\"status\":\"RECONCILIATION_QUEUED\"}");
        });
        configureDeployment();

        var response = service().reconcile("dep-1", "neutral-source");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(assertionClaims(assertion.get()).path("scopes").toString())
            .isEqualTo("[\"runtime:connector:write\"]");
        verify(accessService).requireDeploymentOperatorAccess(any(DeploymentEntity.class));
        verify(auditService).record(
            eq("DEPLOYMENT_INTEGRATION_RECONCILE_REQUESTED"),
            eq("DEPLOYMENT"),
            eq("dep-1"),
            any()
        );
    }

    @Test
    void activeVersionWithoutExternalHttpDatasetFailsClosed() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));
        configureDeployment();
        DeploymentVersionEntity version = integrationVersion();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[]}");
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service().overview("dep-1"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not claim an external HTTP integration");
    }

    @Test
    void conversationHandoverOverviewUsesReadScopeAndSafeOperatorProjection() throws Exception {
        List<String> paths = new ArrayList<>();
        List<String> assertions = new ArrayList<>();
        startServer(exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            assertions.add(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER));
            respond(exchange, 200, exchange.getRequestURI().getPath().contains("connector")
                ? "{\"enabled\":true,\"providerConfigured\":true}"
                : "{\"enabled\":true,\"queued\":1}");
        });
        configureDeployment();
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(handoverVersion()));

        var response = service().conversationHandoverOverview("dep-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(paths).containsExactly(
            "/api/admin/conversation-handover/overview",
            "/api/admin/connector/conversation-channel"
        );
        for (String value : assertions) {
            assertThat(assertionClaims(value).path("scopes").toString())
                .isEqualTo("[\"runtime:conversation-handover:read\"]");
        }
        assertThat(response.body().path("runtime").path("queued").asInt()).isEqualTo(1);
        assertThat(response.body().path("connector").path("reachable").asBoolean()).isTrue();
        assertThat(response.body().path("operatorConsoleUrl").asText())
            .isEqualTo("https://inbox.example/operators");
        assertThat(response.body().toString()).doesNotContain("INBOX_PROVIDER_API_KEY");
    }

    @Test
    void conversationHandoverRetryUsesRecoveryScopeAndAudits() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> assertion = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            assertion.set(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER));
            respond(exchange, 202, "{\"status\":\"RETRY_QUEUED\"}");
        });
        configureDeployment();
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(handoverVersion()));

        var response = service().retryConversationHandoverDispatch("dep-1", "dsp-123");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(path.get()).isEqualTo("/api/admin/conversation-handover/dispatches/dsp-123/retry");
        assertThat(assertionClaims(assertion.get()).path("scopes").toString())
            .isEqualTo("[\"runtime:conversation-handover:recover\"]");
        verify(accessService).requireDeploymentOperatorAccess(any(DeploymentEntity.class));
        verify(auditService).record(
            eq("CONVERSATION_HANDOVER_DISPATCH_RETRY_REQUESTED"),
            eq("DEPLOYMENT"),
            eq("dep-1"),
            any()
        );
    }

    private DeploymentIntegrationOperationsService service() {
        return new DeploymentIntegrationOperationsService(
            deploymentRepository,
            versionRepository,
            accessService,
            secretService,
            auditService,
            objectMapper
        );
    }

    private void configureDeployment() {
        authenticate();
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setActiveVersionId("ver-1");
        deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(accessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-1")).thenReturn(Optional.of(integrationVersion()));
        when(secretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("trusted-runtime-key");
        when(secretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME))
            .thenReturn("fixture-private-assertion-signing-key");
    }

    private DeploymentVersionEntity integrationVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"datasetId":"neutral-data","ingestionMode":"EXTERNAL_SYNC_HTTP"}]}
            """);
        return version;
    }

    private DeploymentVersionEntity handoverVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setBehaviorConfigJson("""
            {
              "executionExtensions": ["CONVERSATION_HANDOVER"],
              "conversationHandover": {
                "enabled": true,
                "channel": {
                  "operatorConsoleUrl": "https://inbox.example/operators",
                  "providerApiKeySecretRef": "INBOX_PROVIDER_API_KEY"
                }
              }
            }
            """);
        return version;
    }

    private void authenticate() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "operator@example.com",
            PlatformRole.PLATFORM_OPERATOR,
            "Operator",
            "SESSION"
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(principal.role().authority()))
            )
        );
    }

    private JsonNode assertionClaims(String authorization) throws Exception {
        assertThat(authorization).startsWith("Bearer rpa1.");
        String token = authorization.substring("Bearer ".length());
        String payload = token.split("\\.")[1];
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> handler.handle(exchange));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
