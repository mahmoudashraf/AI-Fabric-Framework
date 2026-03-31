package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocImportRunEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRecordRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocImportRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentPocImportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void importDatasetPushesBoundedBatchThroughConnectorAndPersistsRun() throws Exception {
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/api/ai/data-sync/batch", exchange -> {
                capturedApiKey.set(exchange.getRequestHeaders().getFirst("X-AIFABRIC-API-KEY"));
                capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                writeJson(
                    exchange,
                    200,
                    """
                        {
                          "success": true,
                          "message": "Imported into runtime",
                          "succeededOperations": 2,
                          "failedOperations": 0
                        }
                        """
                );
            });
            server.start();

            DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
            DeploymentPocImportRunRepository importRunRepository = mock(DeploymentPocImportRunRepository.class);
            DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

            DeploymentEntity deployment = new DeploymentEntity();
            deployment.setId("dep-123");
            deployment.setConnectorBaseUrl("http://localhost:" + server.getAddress().getPort());

            when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
            when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");
            when(importRunRepository.save(org.mockito.ArgumentMatchers.any(DeploymentPocImportRunEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            DeploymentPocImportService service = new DeploymentPocImportService(
                deploymentRepository,
                importRunRepository,
                deploymentAccessService,
                platformSecretService,
                platformAuditService,
                objectMapper
            );
            authenticateOperator();

            var summary = service.importDataset(
                "dep-123",
                new DeploymentPocImportRequest(
                    "Catalog smoke",
                    "product",
                    List.of(
                        new DeploymentPocImportRecordRequest(
                            "SKU-1",
                            "Premium trail shoes.",
                            null,
                            java.util.Map.of("category", "Footwear")
                        ),
                        new DeploymentPocImportRecordRequest(
                            "SKU-2",
                            null,
                            java.util.Map.of("title", "Support plan", "description", "Two-year coverage"),
                            null
                        )
                    )
                )
            );

            assertThat(capturedApiKey.get()).isEqualTo("connector-secret");
            JsonNode requestBody = objectMapper.readTree(capturedBody.get());
            assertThat(requestBody.path("operations")).hasSize(2);
            assertThat(requestBody.path("operations").get(0).path("type").asText()).isEqualTo("UPSERT");
            assertThat(requestBody.path("operations").get(0).path("vectorSpace").asText()).isEqualTo("product");
            assertThat(requestBody.path("trace").path("metadata").path("datasetLabel").asText()).isEqualTo("Catalog smoke");
            assertThat(requestBody.path("trace").path("metadata").path("deploymentId").asText()).isEqualTo("dep-123");
            assertThat(requestBody.path("trace").path("userId").asText()).startsWith("platform-poc-import-");

            assertThat(summary.status()).isEqualTo("SUCCEEDED");
            assertThat(summary.importedCount()).isEqualTo(2);
            assertThat(summary.failedCount()).isEqualTo(0);
            assertThat(summary.createdByDisplayName()).isEqualTo("Operator");

            ArgumentCaptor<DeploymentPocImportRunEntity> entityCaptor = ArgumentCaptor.forClass(DeploymentPocImportRunEntity.class);
            verify(importRunRepository).save(entityCaptor.capture());
            assertThat(entityCaptor.getValue().getDatasetLabel()).isEqualTo("Catalog smoke");
            assertThat(entityCaptor.getValue().getVectorSpace()).isEqualTo("product");
            assertThat(entityCaptor.getValue().getStatus()).isEqualTo("SUCCEEDED");

            verify(platformAuditService).record(
                eq("DEPLOYMENT_POC_IMPORT_EXECUTED"),
                eq("DEPLOYMENT"),
                eq("dep-123"),
                anyMap()
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void importDatasetRejectsRecordsWithoutContentOrEntity() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentPocImportRunRepository importRunRepository = mock(DeploymentPocImportRunRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setConnectorBaseUrl("https://connector.example.com");

        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");

        DeploymentPocImportService service = new DeploymentPocImportService(
            deploymentRepository,
            importRunRepository,
            deploymentAccessService,
            platformSecretService,
            platformAuditService,
            objectMapper
        );

        assertThatThrownBy(() -> service.importDataset(
            "dep-123",
            new DeploymentPocImportRequest(
                "Broken payload",
                "product",
                List.of(new DeploymentPocImportRecordRequest("SKU-1", null, null, null))
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Each import record requires content or entity fields.");
    }

    private void authenticateOperator() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "operator@example.com",
            PlatformRole.PLATFORM_OPERATOR,
            "Operator",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
