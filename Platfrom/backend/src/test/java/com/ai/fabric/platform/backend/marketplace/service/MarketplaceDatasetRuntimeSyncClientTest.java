package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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
        server = server(observedRequest, "{\"success\":true,\"succeededOperations\":1,\"failedOperations\":0}");

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(OBJECT_MAPPER, platformSecretService);
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
                Map.of("source", "test")
            ))
        );

        assertThat(synced).isEqualTo(1);
        JsonNode authContext = observedRequest.get().path("trace").path("authContext");
        assertThat(authContext.path("subjectId").asText()).isEqualTo("system:platform-marketplace-dataset-sync");
        assertThat(authContext.path("grantedScopes")).extracting(JsonNode::asText)
            .containsExactly("data-sync:upsert", "vectorization:verification");
    }

    @Test
    void deleteDocuments_shouldSendDeleteScope() throws Exception {
        AtomicReference<JsonNode> observedRequest = new AtomicReference<>();
        server = server(observedRequest, "{\"success\":true,\"succeededOperations\":1,\"failedOperations\":0}");

        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");

        MarketplaceDatasetRuntimeSyncClient client = new MarketplaceDatasetRuntimeSyncClient(OBJECT_MAPPER, platformSecretService);
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

    private HttpServer server(AtomicReference<JsonNode> observedRequest, String responseBody) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/ai/data-sync/batch", exchange -> {
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
