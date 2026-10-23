package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.admission.CustomerSourcePrerequisiteRefreshService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentIntegrationOperationsServiceTest {

    private final DeploymentTargetContextService targetContexts = mock(DeploymentTargetContextService.class);
    private final DeploymentRuntimeAuthorityService runtimeAuthority = mock(DeploymentRuntimeAuthorityService.class);
    private final CustomerSelfServiceOperationBoundary offerBoundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final CustomerSourcePrerequisiteRefreshService sourcePrerequisites = mock(CustomerSourcePrerequisiteRefreshService.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void overviewUsesExactTargetReleaseAndReadScope() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> trustedKey = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            trustedKey.set(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER));
            respond(exchange, 200, "{\"status\":\"READY\"}");
        });
        configure(integrationVersion());

        var response = service().overview(target());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(path.get()).isEqualTo("/api/admin/connector/integrations");
        assertThat(trustedKey.get()).isEqualTo("target-bound-key");
        verify(targetContexts).resolve("dep-1", "target-1", "rel-1", false);
        verify(offerBoundary).requireOperationalRead(any(), eq("target-1"), eq("rel-1"), org.mockito.ArgumentMatchers.isNull());
        verify(offerBoundary, never()).require(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_READ), Duration.ofMinutes(5)
        );
        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void reconcileUsesExactTargetReleaseWriteScopeAndAudits() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        startServer(exchange -> {
            method.set(exchange.getRequestMethod());
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 202, "{\"status\":\"RECONCILIATION_QUEUED\"}");
        });
        configure(integrationVersion());

        var response = service().reconcile(target(), "neutral-source", "reconcile-1");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(key.get()).isEqualTo("reconcile-1");
        verify(targetContexts).resolve("dep-1", "target-1", "rel-1", true);
        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_WRITE), Duration.ofMinutes(5)
        );
        verify(sourcePrerequisites).ensureFresh(eq(target()),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_REFRESH),
            eq("reconcile-1"), eq(Set.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC)));
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_REFRESH),
            eq("reconcile-1"), eq(Map.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Setting.DOCUMENT_AUDIENCE,
                objectMapper.getNodeFactory().textNode("PUBLIC"))), any(), any(),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC));
        verify(auditService).record(
            eq("DEPLOYMENT_INTEGRATION_RECONCILE_REQUESTED"), eq("DEPLOYMENT"), eq("dep-1"), any()
        );
    }

    @Test
    void webhookReplayUsesConfiguredSourceAudienceAndWriteScope() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 202, "{\"status\":\"REPLAY_QUEUED\"}");
        });
        configure(integrationVersion());

        var response = service().replayWebhook(target(), "neutral-events", "event-1", "replay-1");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(path.get()).isEqualTo("/api/admin/connector/integrations/webhooks/neutral-events/events/event-1/replay");
        assertThat(key.get()).isEqualTo("replay-1");
        verify(sourcePrerequisites).ensureFresh(eq(target()),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_REFRESH),
            eq("replay-1"), eq(Set.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC)));
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_REFRESH),
            eq("replay-1"), any(), any(), any(),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC));
        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONNECTOR_WRITE), Duration.ofMinutes(5)
        );
    }

    @Test
    void unknownSourceIdFailsBeforeRuntimeOrAuthorization() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));
        configure(integrationVersion());

        assertThatThrownBy(() -> service().reconcile(target(), "unreviewed-source", "reconcile-unknown"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not present in the immutable release configuration");

        verify(sourcePrerequisites, never()).ensureFresh(any(), any(), any(), any());
        verify(offerBoundary, never()).require(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(runtimeAuthority, never()).issuePlatformProxyHeaders(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sourceWithoutAudienceFailsClosedWhenOfferReviewsMoreThanOneAudience() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));
        DeploymentVersionEntity version = integrationVersion();
        version.setKnowledgeSourceConfigJson("{\"sources\":[{\"datasetRef\":\"neutral-data\"}]}");
        configure(version);
        when(offerBoundary.reviewedDocumentAudiences(any())).thenReturn(Set.of(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.PUBLIC,
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.AUTHENTICATED));

        assertThatThrownBy(() -> service().reconcile(target(), "neutral-source", "reconcile-ambiguous"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not declare a unique reviewed document audience");

        verify(sourcePrerequisites, never()).ensureFresh(any(), any(), any(), any());
        verify(offerBoundary, never()).require(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(runtimeAuthority, never()).issuePlatformProxyHeaders(any(), any(), any(), any(), any(), any());
    }

    @Test
    void sourceWithoutAudienceUsesTheSoleReviewedAudience() throws Exception {
        startServer(exchange -> respond(exchange, 202, "{\"status\":\"RECONCILIATION_QUEUED\"}"));
        DeploymentVersionEntity version = integrationVersion();
        version.setKnowledgeSourceConfigJson("{\"sources\":[{\"datasetRef\":\"neutral-data\"}]}");
        configure(version);
        when(offerBoundary.reviewedDocumentAudiences(any())).thenReturn(Set.of(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.AUTHENTICATED));

        assertThat(service().reconcile(target(), "neutral-source", "reconcile-reviewed").statusCode()).isEqualTo(202);

        verify(sourcePrerequisites).ensureFresh(eq(target()),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_REFRESH),
            eq("reconcile-reviewed"), eq(Set.of(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.AUTHENTICATED)));
        verify(offerBoundary).require(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.SOURCE_REFRESH),
            eq("reconcile-reviewed"), any(), any(), any(),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience.AUTHENTICATED));
    }

    @Test
    void selectedReleaseWithoutExternalHttpDatasetFailsClosed() throws Exception {
        startServer(exchange -> respond(exchange, 500, "{}"));
        DeploymentVersionEntity version = integrationVersion();
        version.setMarketplaceDatasetConfigJson("{\"datasets\":[]}");
        configure(version);

        assertThatThrownBy(() -> service().overview(target()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("selected release version does not claim an external HTTP integration");
        verify(runtimeAuthority, never()).issuePlatformProxyHeaders(any(), any(), any(), any(), any(), any());
    }

    @Test
    void conversationHandoverOverviewUsesSelectedReleaseAndSafeProjection() throws Exception {
        List<String> paths = new ArrayList<>();
        startServer(exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            respond(exchange, 200, exchange.getRequestURI().getPath().contains("connector")
                ? "{\"enabled\":true,\"providerConfigured\":true}"
                : "{\"enabled\":true,\"queued\":1}");
        });
        configure(handoverVersion());

        var response = service().conversationHandoverOverview(target());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(paths).containsExactly(
            "/api/admin/conversation-handover/overview",
            "/api/admin/connector/conversation-channel"
        );
        verify(offerBoundary).requireOperationalRead(any(), eq("target-1"), eq("rel-1"), org.mockito.ArgumentMatchers.isNull());
        verify(offerBoundary, never()).requireBindingKind(any(), any(), any(), any(), any(), any());
        verify(runtimeAuthority, org.mockito.Mockito.times(2)).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_READ), Duration.ofMinutes(5)
        );
        assertThat(response.body().path("runtime").path("queued").asInt()).isEqualTo(1);
        assertThat(response.body().path("connector").path("reachable").asBoolean()).isTrue();
        assertThat(response.body().path("operatorConsoleUrl").asText()).isEqualTo("https://inbox.example/operators");
        assertThat(response.body().toString()).doesNotContain("INBOX_PROVIDER_API_KEY");
    }

    @Test
    void conversationHandoverRetryUsesRecoveryScopeAndAudits() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 202, "{\"status\":\"RETRY_QUEUED\"}");
        });
        configure(handoverVersion());

        var response = service().retryConversationHandoverDispatch(target(), "dsp-123", "retry-1");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(path.get()).isEqualTo("/api/admin/conversation-handover/dispatches/dsp-123/retry");
        assertThat(key.get()).isEqualTo("retry-1");
        verify(runtimeAuthority).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_RECOVER), Duration.ofMinutes(5)
        );
        verify(offerBoundary).requireBindingKind(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.INBOX_OPERATOR_DELIVER),
            eq("retry-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingKind.INBOX_OPERATOR));
        verify(auditService).record(
            eq("CONVERSATION_HANDOVER_DISPATCH_RETRY_REQUESTED"), eq("DEPLOYMENT"), eq("dep-1"), any()
        );
    }

    @Test
    void conversationHandoverExpiryUsesReviewedInboxBinding() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        startServer(exchange -> {
            path.set(exchange.getRequestURI().getPath());
            key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 202, "{\"status\":\"EXPIRY_QUEUED\"}");
        });
        configure(handoverVersion());

        var response = service().expireConversationHandover(target(), "handover-1", "expire-1");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(path.get()).isEqualTo("/api/admin/conversation-handover/handovers/handover-1/expire");
        assertThat(key.get()).isEqualTo("expire-1");
        verify(offerBoundary).requireBindingKind(any(), eq("target-1"), eq("rel-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.INBOX_OPERATOR_DELIVER),
            eq("expire-1"),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.BindingKind.INBOX_OPERATOR));
        verify(runtimeAuthority, times(1)).issuePlatformProxyHeaders(
            "dep-1", "target-1", "rel-1", "sha256:authority-1",
            List.of(RuntimePrivateAccessSupport.SCOPE_RUNTIME_CONVERSATION_HANDOVER_RECOVER), Duration.ofMinutes(5)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"reconcile", "replay", "retry", "expire"})
    void mutationsRejectMissingOrInvalidKeysBeforeAuthorizationAndNetwork(String operation) {
        for (String key : new String[] {null, "", " ", " key", "key ", "key\n", "x".repeat(129)}) {
            assertThatThrownBy(() -> {
                switch (operation) {
                    case "reconcile" -> service().reconcile(target(), "neutral-source", key);
                    case "replay" -> service().replayWebhook(target(), "neutral-events", "event-1", key);
                    case "retry" -> service().retryConversationHandoverDispatch(target(), "dispatch-1", key);
                    case "expire" -> service().expireConversationHandover(target(), "handover-1", key);
                    default -> throw new IllegalArgumentException(operation);
                }
            }).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Idempotency-Key is required");
        }
        org.mockito.Mockito.verifyNoInteractions(targetContexts, offerBoundary, sourcePrerequisites, runtimeAuthority);
    }

    private DeploymentIntegrationOperationsService service() {
        return new DeploymentIntegrationOperationsService(targetContexts, runtimeAuthority, offerBoundary,
            sourcePrerequisites, auditService, objectMapper);
    }

    private void configure(DeploymentVersionEntity version) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("target-1");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setDeploymentId("dep-1");
        release.setDeploymentVersionId("ver-1");
        release.setTargetProfileId("target-1");
        release.setRuntimeAuthorityFingerprint("sha256:authority-1");
        var context = new DeploymentTargetContextService.Context(
            deployment, profile, release, version,
            "http://localhost:" + server.getAddress().getPort(), null
        );
        when(targetContexts.resolve("dep-1", "target-1", "rel-1", false)).thenReturn(context);
        when(targetContexts.resolve("dep-1", "target-1", "rel-1", true)).thenReturn(context);
        when(runtimeAuthority.issuePlatformProxyHeaders(any(), any(), any(), any(), any(), any()))
            .thenReturn(Map.of(
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER, "target-bound-key",
                RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER, "Bearer target-bound-assertion"
            ));
    }

    private DeploymentTargetReference target() {
        return new DeploymentTargetReference("dep-1", "target-1", "rel-1");
    }

    private DeploymentVersionEntity integrationVersion() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setMarketplaceDatasetConfigJson("""
            {"datasets":[{
              "datasetId":"neutral-data",
              "ingestionMode":"EXTERNAL_SYNC_HTTP",
              "syncConnector": {
                "httpSource": {"sourceId":"neutral-source"},
                "webhook": {"sourceId":"neutral-events"}
              }
            }]}
            """);
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{
              "datasetRef":"neutral-data",
              "authModes":["PUBLIC_RUNTIME_ANONYMOUS"]
            }]}
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
