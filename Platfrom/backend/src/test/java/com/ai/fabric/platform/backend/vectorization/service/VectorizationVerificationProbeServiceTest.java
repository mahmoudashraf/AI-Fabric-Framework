package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorizationVerificationProbeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertSentinelPrefersTrustedRuntimeOperationalSurfaceWhenConfigured() throws Exception {
        AtomicReference<String> capturedRuntimeApiKey = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/ai/data-sync/upsert", exchange -> {
                capturedRuntimeApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-RUNTIME-API-KEY"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"success\":true}");
            });
            server.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");

            VectorizationVerificationProbeService service = new VectorizationVerificationProbeService(
                objectMapper,
                platformSecretService
            );

            DeploymentEntity deployment = new DeploymentEntity();
            deployment.setId("dep-123");
            deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());
            deployment.setConnectorBaseUrl("http://connector-should-not-be-used.local");

            JsonNode response = service.upsertSentinel(
                deployment,
                "product",
                "SKU-1",
                "Trail shoe verification probe",
                Map.of("probe", true),
                "trace-123"
            );

            assertThat(response.path("success").asBoolean()).isTrue();
            assertThat(capturedRuntimeApiKey.get()).isEqualTo("runtime-secret");
            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("trace").path("userId").asText())
                .isEqualTo("system:platform-vectorization-verification");
            assertThat(requestBody.path("trace").path("sessionId").asText())
                .isEqualTo("system:tenant-isolation-smoke");
            assertThat(requestBody.path("trace").path("authContext").path("subjectType").asText())
                .isEqualTo("SYSTEM_PROCESS");
            assertThat(requestBody.path("trace").path("authContext").path("authMode").asText())
                .isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
            assertThat(requestBody.path("trace").path("metadata").path("verificationProbe").asText())
                .isEqualTo("TENANT_SHARED_ISOLATION_SMOKE");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void upsertSentinelFallsBackToConnectorCompatibilityWhenTrustedRuntimeAuthIsUnavailable() throws Exception {
        AtomicReference<String> capturedConnectorApiKey = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/ai/data-sync/upsert", exchange -> {
                capturedConnectorApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-API-KEY"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(exchange, 200, "{\"success\":true}");
            });
            server.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(null);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");

            VectorizationVerificationProbeService service = new VectorizationVerificationProbeService(
                objectMapper,
                platformSecretService
            );

            DeploymentEntity deployment = new DeploymentEntity();
            deployment.setId("dep-456");
            deployment.setConnectorBaseUrl("http://localhost:" + server.getAddress().getPort());

            JsonNode response = service.upsertSentinel(
                deployment,
                "product",
                "SKU-2",
                "Connector fallback verification probe",
                Map.of("probe", true),
                "trace-456"
            );

            assertThat(response.path("success").asBoolean()).isTrue();
            assertThat(capturedConnectorApiKey.get()).isEqualTo("connector-secret");
            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("trace").path("authContext").path("callerType").asText())
                .isEqualTo("SYSTEM_PROCESS");
            assertThat(requestBody.path("trace").path("metadata").path("verificationProbe").asText())
                .isEqualTo("TENANT_SHARED_ISOLATION_SMOKE");
        } finally {
            server.stop(0);
        }
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
