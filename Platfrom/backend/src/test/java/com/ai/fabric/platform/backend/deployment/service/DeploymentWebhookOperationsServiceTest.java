package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentWebhookOperationsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getOverviewProxiesRuntimeWebhookAdminSurface() throws Exception {
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/admin/webhooks/overview", exchange -> {
                assertThat(exchange.getRequestURI().getQuery()).isEqualTo("limit=25");
                assertThat(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER))
                    .isEqualTo("trusted-backend-key");
                capturedAuthHeader.set(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "counts": {
                            "PENDING": 1,
                            "RETRY_PENDING": 0,
                            "IN_PROGRESS": 0,
                            "DELIVERED": 3,
                            "FAILED": 1
                          },
                          "recentDeliveries": [],
                          "retryEligibleCount": 1
                        }
                        """
                );
            });
            server.start();

            DeploymentWebhookOperationsService service = serviceFor(server, mock(PlatformAuditService.class));
            JsonNode response = service.getOverview("dep-123", 25);

            assertThat(capturedAuthHeader.get()).startsWith("Bearer rpa1.");
            assertThat(response.path("success").asBoolean()).isTrue();
            assertThat(response.path("counts").path("DELIVERED").asInt()).isEqualTo(3);
            assertThat(response.path("retryEligibleCount").asInt()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retryDeliveryAuditsAndQueuesRuntimeRetry() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/admin/webhooks/deliveries/whd-1/retry", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER))
                    .isEqualTo("trusted-backend-key");
                assertThat(exchange.getRequestHeaders().getFirst(RuntimePrivateAccessSupport.PRIVATE_AUTHORIZATION_HEADER))
                    .startsWith("Bearer rpa1.");
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "deliveryId": "whd-1",
                          "status": "RETRY_PENDING",
                          "summaryMessage": "Webhook delivery queued for retry."
                        }
                        """
                );
            });
            server.start();

            PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
            DeploymentWebhookOperationsService service = serviceFor(server, platformAuditService);
            JsonNode response = service.retryDelivery("dep-123", "whd-1");

            assertThat(capturedBody.get()).isEqualTo("{}");
            assertThat(response.path("status").asText()).isEqualTo("RETRY_PENDING");
            verify(platformAuditService).record(
                eq("DEPLOYMENT_WEBHOOK_DELIVERY_RETRY_TRIGGERED"),
                eq("DEPLOYMENT"),
                eq("dep-123"),
                anyMap()
            );
        } finally {
            server.stop(0);
        }
    }

    private DeploymentWebhookOperationsService serviceFor(HttpServer server, PlatformAuditService platformAuditService) {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME))
            .thenReturn("trusted-backend-key");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY"))
            .thenReturn("private-assertion-key");

        return new DeploymentWebhookOperationsService(
            deploymentRepository,
            deploymentAccessService,
            platformSecretService,
            platformAuditService,
            objectMapper
        );
    }

    private void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
