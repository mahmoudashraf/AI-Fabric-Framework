package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.vectorization.runner.config.VectorizationRunnerProperties;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorDataSyncTargetWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConnectorDataSyncTargetWriter writer = new ConnectorDataSyncTargetWriter(
        objectMapper,
        new VectorizationRunnerProperties(
            "https://platform.example",
            "token",
            "runner-1",
            "dep-1",
            "2026.04.track-b",
            "1",
            Duration.ofSeconds(15),
            Duration.ofMinutes(5)
        )
    );

    @Test
    void upsertBatchSendsVerifiedAuthContextToRuntimeDataSync() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                {"success":true,"totalOperations":1,"succeededOperations":1,"failedOperations":0,"results":[]}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            var result = writer.upsertBatch(
                bundle,
                "policy",
                List.of(new VectorizationMappedRecord("policy", "policy-1", "policy-1", null, Map.of("title", "Policy"), Map.of()))
            );

            assertThat(result.succeeded()).isEqualTo(1);
            assertThat(result.failed()).isZero();
            assertThat(capturedApiKey.get()).isEqualTo("runtime-secret");
            JsonNode request = objectMapper.readTree(capturedBody.get());
            assertThat(request.path("trace").path("authContext").path("subjectId").asText()).isEqualTo("system:platform-vectorization-runner");
            assertThat(request.path("trace").path("authContext").path("deploymentId").asText()).isEqualTo("dep-1");
            assertThat(request.path("operations")).hasSize(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upsertBatchRejectsUnstructuredSuccessEnvelope() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            byte[] body = """
                {"success":false,"errorCode":"SERVICE_UNAVAILABLE","message":"Connector service unavailable."}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            assertThatThrownBy(() -> writer.upsertBatch(
                bundle,
                "policy",
                List.of(new VectorizationMappedRecord("policy", "policy-1", "policy-1", null, Map.of("title", "Policy"), Map.of()))
            ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Vectorization target rejected batch");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upsertBatchIncludesFirstFailureDetailsWhenBatchReturnsOperationFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/ai/data-sync/batch", exchange -> {
            byte[] body = """
                {
                  "success": false,
                  "message": "Completed with failures",
                  "failedOperations": 2,
                  "results": [
                    {
                      "success": false,
                      "errorCode": "VECTOR_STORE_FAILED",
                      "vectorSpace": "product",
                      "id": "sku-1",
                      "message": "Vector store failed.",
                      "metadata": {
                        "cause": "Field [vector] vector's dimensions must be <= [1024]; got 1536"
                      }
                    }
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            VectorizationExecutionBundle bundle = bundle("http://localhost:" + server.getAddress().getPort());

            assertThatThrownBy(() -> writer.upsertBatch(
                bundle,
                "product",
                List.of(new VectorizationMappedRecord("product", "sku-1", "sku-1", null, Map.of("name", "Laptop"), Map.of()))
            ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Completed with failures")
                .hasMessageContaining("failedOperations=2")
                .hasMessageContaining("product/sku-1[VECTOR_STORE_FAILED]")
                .hasMessageContaining("cause=Field [vector] vector's dimensions must be <= [1024]; got 1536");
        } finally {
            server.stop(0);
        }
    }

    private VectorizationExecutionBundle bundle(String baseUrl) {
        ObjectNode empty = objectMapper.createObjectNode();
        ObjectNode authContext = objectMapper.createObjectNode();
        authContext.put("subjectId", "system:platform-vectorization-runner");
        authContext.put("subjectType", "SYSTEM_PROCESS");
        authContext.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        authContext.put("callerType", "SYSTEM_PROCESS");
        authContext.put("sessionId", "run-1");
        authContext.put("deploymentId", "dep-1");
        authContext.put("customerId", "cus-1");
        authContext.put("tenantId", "ten-1");
        authContext.put("issuer", "platform-vectorization-runner");
        authContext.putArray("grantedScopes").add("data-sync:upsert").add("data-sync:delete").add("vectorization:runner");
        return new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.BOOTSTRAP,
            "rev-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("policy"),
            empty,
            empty,
            new ConnectionDescriptor("vcn-1", "Primary", "REST_API", "NONE", empty, empty),
            new ResolvedSourceAuthMaterial(Map.of(), Map.of()),
            authContext,
            new TargetConnectionDescriptor("RUNTIME_DATA_SYNC", baseUrl, "/api/ai/data-sync/batch", "/api/ai/data-sync/vector-spaces", "X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret")
        );
    }
}
