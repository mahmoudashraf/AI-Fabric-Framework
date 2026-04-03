package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocImportRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocImportRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentPocWorkspaceServiceTest {

    @Test
    void workspaceSummaryIncludesDatasetProfileAndRuntimeIndexingCounts() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/admin/indexing/overview", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY")).isEqualTo("test-admin");
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "countsByEntityType": {
                            "product": 12,
                            "review": 4,
                            "policy": 2
                          },
                          "vectorDb": "LuceneVectorDatabaseService",
                          "totalVectors": 18,
                          "supportsVectorScan": true
                        }
                        """
                );
            });
            server.start();

            DeploymentPocWorkspaceService service = serviceFor(server);
            var summary = service.getWorkspace("dep-123");

            assertThat(summary.dataset().profileId()).isEqualTo("ecommerce-demo");
            assertThat(summary.dataset().entityTypes()).containsExactly("product", "review", "policy");
            assertThat(summary.dataset().upstreamBaseUrl()).isEqualTo("https://store.example.com");
            assertThat(summary.indexing().available()).isTrue();
            assertThat(summary.indexing().countsByEntityType()).containsEntry("product", 12L);
            assertThat(summary.resetCapabilities().clearRuntimeVectors()).isTrue();
            assertThat(summary.recentImports()).singleElement().satisfies(importRun -> {
                assertThat(importRun.datasetLabel()).isEqualTo("Catalog import");
                assertThat(importRun.vectorSpace()).isEqualTo("product");
                assertThat(importRun.status()).isEqualTo("SUCCEEDED");
            });
            assertThat(summary.warnings()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clearRuntimeVectorsUsesRuntimeAdminEndpointAndAuditsResult() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/admin/migration/clear", exchange -> {
                assertThat(exchange.getRequestHeaders().getFirst("X-ADMIN-API-KEY")).isEqualTo("test-admin");
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "message": "Migration clear completed",
                          "result": {
                            "clearedVectors": true,
                            "removedVectors": 19
                          }
                        }
                        """
                );
            });
            server.start();

            PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
            DeploymentPocWorkspaceService service = serviceFor(server, platformAuditService);
            var response = service.clearRuntimeVectors(
                "dep-123",
                new DeploymentPocRuntimeResetRequest(true, "operator-reset")
            );

            assertThat(capturedBody.get()).contains("\"confirm\":true");
            assertThat(capturedBody.get()).contains("\"reason\":\"operator-reset\"");
            assertThat(response.success()).isTrue();
            assertThat(response.clearedVectors()).isTrue();
            assertThat(response.removedVectors()).isEqualTo(19L);
            verify(platformAuditService).record(
                eq("DEPLOYMENT_POC_RUNTIME_VECTORS_CLEARED"),
                eq("DEPLOYMENT"),
                eq("dep-123"),
                anyMap()
            );
        } finally {
            server.stop(0);
        }
    }

    private DeploymentPocWorkspaceService serviceFor(HttpServer server) {
        return serviceFor(server, mock(PlatformAuditService.class));
    }

    private DeploymentPocWorkspaceService serviceFor(HttpServer server, PlatformAuditService platformAuditService) {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentPocImportRunRepository deploymentPocImportRunRepository = mock(DeploymentPocImportRunRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl("http://localhost:" + server.getAddress().getPort());

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setEntityConfigJson(
            """
                {
                  "ai-entities": {
                    "product": {},
                    "review": {},
                    "policy": {}
                  }
                }
                """
        );
        version.setRoutingConfigJson(
            """
                {
                  "connector": {
                    "upstream": {
                      "base-url": "https://store.example.com"
                    }
                  }
                }
                """
        );

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(deploymentPocImportRunRepository.findTop10ByDeploymentIdOrderByCreatedAtDesc("dep-123"))
            .thenReturn(List.of(importRun()));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("test-admin");

        return new DeploymentPocWorkspaceService(
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            deploymentPocImportRunRepository,
            deploymentAccessService,
            platformSecretService,
            platformAuditService,
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
    }

    private DeploymentPocImportRunEntity importRun() {
        DeploymentPocImportRunEntity entity = new DeploymentPocImportRunEntity();
        entity.setId("pir-1234");
        entity.setDeploymentId("dep-123");
        entity.setDatasetLabel("Catalog import");
        entity.setSourceType("JSON_UPLOAD");
        entity.setVectorSpace("product");
        entity.setStatus("SUCCEEDED");
        entity.setRecordCount(3);
        entity.setImportedCount(3);
        entity.setFailedCount(0);
        entity.setCreatedByActorId("usr-123");
        entity.setCreatedByDisplayName("Operator");
        entity.setCreatedAt(Instant.parse("2026-03-31T10:15:30Z"));
        return entity;
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
