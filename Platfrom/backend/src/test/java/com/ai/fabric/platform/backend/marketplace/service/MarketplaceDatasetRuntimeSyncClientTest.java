package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketplaceDatasetRuntimeSyncClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void upsertDocuments_shouldSendUpsertScope() throws Exception {
        AtomicReference<JsonNode> observedRequest = new AtomicReference<>();
        AtomicReference<String> observedRuntimeApiKey = new AtomicReference<>();
        AtomicReference<String> observedRuntimeAuthorization = new AtomicReference<>();
        server = server(
            observedRequest,
            observedRuntimeApiKey,
            observedRuntimeAuthorization,
            "{\"success\":true,\"succeededOperations\":1,\"failedOperations\":0}"
        );

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("signing-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(
            OBJECT_MAPPER,
            platformSecretService,
            3,
            1
        );
        DeploymentEntity deployment = deployment();

        int synced = client.upsertDocuments(
            deployment,
            "product",
            "dataset-1",
            "handle-1",
            "hash-1",
            List.of(new MarketplaceDatasetSyncService.DatasetDocument(
                "doc-1",
                "hello",
                Map.of(
                    "source", "test",
                    "tenantId", "source-controlled-tenant"
                )
            ))
        );

        assertThat(synced).isEqualTo(1);
        assertThat(observedRuntimeApiKey.get()).isEqualTo("runtime-secret");
        assertThat(observedRuntimeAuthorization.get()).startsWith("Bearer rpa1.");
        JsonNode authContext = observedRequest.get().path("trace").path("authContext");
        assertThat(authContext.path("subjectId").asText()).isEqualTo("system:platform-marketplace-dataset-sync");
        assertThat(authContext.path("grantedScopes")).extracting(JsonNode::asText)
            .containsExactly("data-sync:upsert", "vectorization:verification");
        JsonNode metadata = observedRequest.get()
            .path("operations")
            .path(0)
            .path("metadata");
        assertThat(metadata.path("source").asText()).isEqualTo("test");
        assertThat(metadata.path("deploymentId").asText()).isEqualTo("dep-123");
        assertThat(metadata.path("customerId").asText()).isEqualTo("cus-123");
        assertThat(metadata.path("tenantId").asText()).isEqualTo("ten-123");
        assertThat(metadata.path("knowledgeSourceHandleRef").asText()).isEqualTo("handle-1");
        assertThat(metadata.path("marketplaceDatasetId").asText()).isEqualTo("dataset-1");
        assertThat(metadata.path("marketplaceDatasetHash").asText()).isEqualTo("hash-1");
    }

    @Test
    void deleteDocuments_shouldSendDeleteScope() throws Exception {
        AtomicReference<JsonNode> observedRequest = new AtomicReference<>();
        server = server(
            observedRequest,
            new AtomicReference<>(),
            new AtomicReference<>(),
            "{\"success\":true,\"succeededOperations\":1,\"failedOperations\":0}"
        );

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("signing-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(
            OBJECT_MAPPER,
            platformSecretService,
            3,
            1
        );
        DeploymentEntity deployment = deployment();

        int deleted = client.deleteDocuments(
            deployment,
            "product",
            "dataset-1",
            "handle-1",
            "hash-1",
            List.of("doc-1")
        );

        assertThat(deleted).isEqualTo(1);
        JsonNode authContext = observedRequest.get().path("trace").path("authContext");
        assertThat(authContext.path("grantedScopes")).extracting(JsonNode::asText)
            .containsExactly("data-sync:delete", "vectorization:verification");
    }

    @Test
    void upsertDocuments_shouldRetryTransient404() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            attempts.incrementAndGet();
            if (attempts.get() == 1) {
                writeJson(exchange, 404, "{\"error\":\"warming\"}");
                return;
            }
            writeJson(exchange, 200, "{\"success\":true,\"succeededOperations\":1,\"failedOperations\":0}");
        });
        server.start();

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("signing-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(OBJECT_MAPPER, platformSecretService);

        int synced = client.upsertDocuments(
            deployment(),
            "faq-article",
            "dataset-1",
            "handle-1",
            "hash-1",
            List.of(new MarketplaceDatasetSyncService.DatasetDocument(
                "doc-1",
                "hello",
                Map.of("source", "test")
            ))
        );

        assertThat(synced).isEqualTo(1);
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void upsertDocuments_shouldIncludeRuntimeErrorBodyAfterExhaustingRetryable404() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            attempts.incrementAndGet();
            writeJson(
                exchange,
                404,
                """
                    {
                      "success": false,
                      "errorCode": "VECTOR_SPACE_NOT_FOUND",
                      "message": "Unknown vectorSpace: product",
                      "path": "/api/ai/data-sync/batch"
                    }
                    """
            );
        });
        server.start();

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("signing-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(
            OBJECT_MAPPER,
            platformSecretService,
            2,
            1
        );

        assertThatThrownBy(() -> client.upsertDocuments(
            deployment(),
            "product",
            "dataset-1",
            "handle-1",
            "hash-1",
            List.of(new MarketplaceDatasetSyncService.DatasetDocument(
                "doc-1",
                "hello",
                Map.of("source", "test")
            ))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("HTTP 404")
            .hasMessageContaining("Unknown vectorSpace: product")
            .hasMessageContaining("errorCode=VECTOR_SPACE_NOT_FOUND")
            .hasMessageContaining("path=/api/ai/data-sync/batch");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void upsertDocuments_shouldIncludeFirstFailedOperationInError() throws Exception {
        server = server(
            new AtomicReference<>(),
            new AtomicReference<>(),
            new AtomicReference<>(),
            """
                {
                  "success": false,
                  "message": "Completed with failures",
                  "succeededOperations": 1,
                  "failedOperations": 1,
                  "results": [
                    {"success": true, "id": "ok"},
                    {
                      "success": false,
                      "errorCode": "VECTOR_SPACE_NOT_FOUND",
                      "vectorSpace": "support-policy",
                      "id": "shipping-policy",
                      "message": "Unknown vectorSpace: support-policy"
                    }
                  ]
                }
                """
        );

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn("signing-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(OBJECT_MAPPER, platformSecretService);

        assertThatThrownBy(() -> client.upsertDocuments(
            deployment(),
            "support-policy",
            "policy-folder-pack",
            "handle-1",
            "hash-1",
            List.of(new MarketplaceDatasetSyncService.DatasetDocument(
                "shipping-policy",
                "hello",
                Map.of("source", "test")
            ))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Completed with failures")
            .hasMessageContaining("errorCode=VECTOR_SPACE_NOT_FOUND")
            .hasMessageContaining("vectorSpace=support-policy")
            .hasMessageContaining("id=shipping-policy")
            .hasMessageContaining("Unknown vectorSpace: support-policy");
    }

    private HttpServer server(AtomicReference<JsonNode> observedRequest,
                              AtomicReference<String> observedRuntimeApiKey,
                              AtomicReference<String> observedRuntimeAuthorization,
                              String responseBody) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/ai/data-sync/batch", exchange -> {
            observedRuntimeApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
            observedRuntimeAuthorization.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTHORIZATION"));
            observedRequest.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            writeJson(exchange, 200, responseBody);
        });
        httpServer.start();
        return httpServer;
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("ten-123");
        deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());
        return deployment;
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
