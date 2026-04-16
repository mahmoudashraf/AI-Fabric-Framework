package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedInferenceProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceDependentDeploymentSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceHealthSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformManagedInferenceAdminServiceTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Test
    void listDependentsReportsDraftUsageForManagedServiceRef() {
        PlatformManagedInferenceServiceEntity service = serviceEntity("shared-embeddings-standard", "MANAGED_ENDPOINT_PROFILE", "SHARED_PLATFORM_SERVICE");
        PlatformManagedInferenceEndpointEntity endpoint = endpointEntity(service.getId(), "shared-embeddings-standard", "EMBEDDING");

        PlatformManagedInferenceServiceService serviceService = mock(PlatformManagedInferenceServiceService.class);
        PlatformManagedInferenceProvisioningService provisioningService = mock(PlatformManagedInferenceProvisioningService.class);
        PlatformManagedInferenceServiceRepository serviceRepository = mock(PlatformManagedInferenceServiceRepository.class);
        PlatformManagedInferenceEndpointRepository endpointRepository = mock(PlatformManagedInferenceEndpointRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setName("Support AI");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("ACTIVE");
        deployment.setActiveDraftId("drf-1");
        deployment.setCreatedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());

        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId("drf-1");
        draft.setDeploymentId("dep-1");
        draft.setProviderConfigJson("""
            {
              "embeddingManagedServiceRef": "shared-embeddings-standard"
            }
            """);

        when(serviceService.requireService("shared-embeddings-standard")).thenReturn(service);
        when(endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId())).thenReturn(List.of(endpoint));
        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(deployment));
        when(deploymentDraftRepository.findById("drf-1")).thenReturn(java.util.Optional.of(draft));

        PlatformManagedInferenceAdminService adminService = new PlatformManagedInferenceAdminService(
            serviceService,
            provisioningService,
            serviceRepository,
            endpointRepository,
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            platformSecretService,
            platformAuditService,
            railwayGraphqlClient,
            new ObjectMapper()
        );

        List<PlatformManagedInferenceDependentDeploymentSummary> dependents = adminService.listDependents("shared-embeddings-standard");

        assertThat(dependents).hasSize(1);
        assertThat(dependents.get(0).deploymentId()).isEqualTo("dep-1");
        assertThat(dependents.get(0).currentDraftUsesService()).isTrue();
        assertThat(dependents.get(0).usages()).contains("draft:embedding");
    }

    @Test
    void healthRunsAuthenticatedEmbeddingProbeForOpenAiCompatibleService() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/v1/embeddings", this::handleEmbeddingsRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedInferenceServiceEntity service = serviceEntity("shared-embeddings-standard", "MANAGED_ENDPOINT_PROFILE", "SHARED_PLATFORM_SERVICE");
        service.setBaseUrl(baseUrl);
        service.setSecretName("OPENAI_API_KEY");
        PlatformManagedInferenceEndpointEntity endpoint = endpointEntity(service.getId(), "shared-embeddings-standard", "EMBEDDING");
        endpoint.setBaseUrl(baseUrl + "/v1");
        endpoint.setStatus("ACTIVE");

        PlatformManagedInferenceServiceService serviceService = mock(PlatformManagedInferenceServiceService.class);
        PlatformManagedInferenceProvisioningService provisioningService = mock(PlatformManagedInferenceProvisioningService.class);
        PlatformManagedInferenceServiceRepository serviceRepository = mock(PlatformManagedInferenceServiceRepository.class);
        PlatformManagedInferenceEndpointRepository endpointRepository = mock(PlatformManagedInferenceEndpointRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shared-embeddings-standard")).thenReturn(service);
        when(endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId())).thenReturn(List.of(endpoint));
        when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
        when(platformSecretService.resolveSecret("OPENAI_API_KEY")).thenReturn("test-token");
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformManagedInferenceAdminService adminService = new PlatformManagedInferenceAdminService(
            serviceService,
            provisioningService,
            serviceRepository,
            endpointRepository,
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            platformSecretService,
            platformAuditService,
            railwayGraphqlClient,
            new ObjectMapper()
        );

        PlatformManagedInferenceHealthSummary health = adminService.getHealth("shared-embeddings-standard");

        assertThat(health.status()).isEqualTo("READY");
        assertThat(health.inferenceProbe().status()).isEqualTo("READY");
        assertThat(health.inferenceProbe().endpoint()).isEqualTo(baseUrl + "/v1/embeddings");
        assertThat(health.secretConfigured()).isTrue();
        verify(serviceRepository).save(service);
    }

    @Test
    void rotateSecretUpdatesManagedSecretAndReconcilesService() {
        PlatformManagedInferenceServiceEntity service = serviceEntity("shared-embeddings-standard", "SHARED_EMBEDDING_SERVICE", "SHARED_PLATFORM_SERVICE");
        service.setSecretName("MANAGED_SHARED_INFERENCE_SHARED_EMBEDDINGS_STANDARD_API_KEY");

        PlatformManagedInferenceServiceService serviceService = mock(PlatformManagedInferenceServiceService.class);
        PlatformManagedInferenceProvisioningService provisioningService = mock(PlatformManagedInferenceProvisioningService.class);
        PlatformManagedInferenceServiceRepository serviceRepository = mock(PlatformManagedInferenceServiceRepository.class);
        PlatformManagedInferenceEndpointRepository endpointRepository = mock(PlatformManagedInferenceEndpointRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shared-embeddings-standard")).thenReturn(service);
        when(serviceService.getService("shared-embeddings-standard")).thenReturn(serviceSummary(service));
        when(platformSecretService.isManagedSecretName(service.getSecretName())).thenReturn(true);
        when(platformSecretService.isSecretPresent(service.getSecretName())).thenReturn(true);
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(provisioningService.reconcile("shared-embeddings-standard")).thenReturn(serviceSummary(service));
        doNothing().when(platformSecretService).upsertManagedSecret(eq(service.getSecretName()), eq("new-secret"), any());
        when(endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId())).thenReturn(List.of());

        PlatformManagedInferenceAdminService adminService = new PlatformManagedInferenceAdminService(
            serviceService,
            provisioningService,
            serviceRepository,
            endpointRepository,
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            platformSecretService,
            platformAuditService,
            railwayGraphqlClient,
            new ObjectMapper()
        );

        PlatformManagedInferenceServiceSummary summary = adminService.rotateSecret("shared-embeddings-standard", "new-secret");

        assertThat(summary.serviceRef()).isEqualTo("shared-embeddings-standard");
        verify(platformSecretService).upsertManagedSecret(eq(service.getSecretName()), eq("new-secret"), any());
        verify(provisioningService).reconcile("shared-embeddings-standard");
        verify(serviceService).getService("shared-embeddings-standard");
        verify(serviceRepository, atLeastOnce()).save(service);
    }

    @Test
    void scaleTriggersPostActionVerification() {
        PlatformManagedInferenceServiceEntity service = serviceEntity("onnx-bundled", "BUNDLED_ONNX_SERVICE", "BUNDLED_RUNTIME");
        service.setProviderType("onnx");
        service.setSecretName(null);

        PlatformManagedInferenceServiceService serviceService = mock(PlatformManagedInferenceServiceService.class);
        PlatformManagedInferenceProvisioningService provisioningService = mock(PlatformManagedInferenceProvisioningService.class);
        PlatformManagedInferenceServiceRepository serviceRepository = mock(PlatformManagedInferenceServiceRepository.class);
        PlatformManagedInferenceEndpointRepository endpointRepository = mock(PlatformManagedInferenceEndpointRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository deploymentDraftRepository = mock(DeploymentDraftRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("onnx-bundled")).thenReturn(service);
        when(serviceService.getService("onnx-bundled")).thenReturn(serviceSummary(service));
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.findAllByServiceIdOrderByProfileRefAsc(service.getId())).thenReturn(List.of());
        when(provisioningService.scale("onnx-bundled", 2)).thenReturn(serviceSummary(service));

        PlatformManagedInferenceAdminService adminService = new PlatformManagedInferenceAdminService(
            serviceService,
            provisioningService,
            serviceRepository,
            endpointRepository,
            deploymentRepository,
            deploymentDraftRepository,
            deploymentVersionRepository,
            platformSecretService,
            platformAuditService,
            railwayGraphqlClient,
            new ObjectMapper()
        );

        PlatformManagedInferenceServiceSummary summary = adminService.scale("onnx-bundled", 2);

        assertThat(summary.serviceRef()).isEqualTo("onnx-bundled");
        verify(provisioningService).scale("onnx-bundled", 2);
        verify(serviceService).getService("onnx-bundled");
        verify(serviceRepository, atLeastOnce()).save(service);
    }

    private void handleEmbeddingsRequest(HttpExchange exchange) throws IOException {
        String response = """
            {
              "data": [
                {
                  "embedding": [0.1, 0.2, 0.3]
                }
              ]
            }
            """;
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response.getBytes());
        }
    }

    private PlatformManagedInferenceServiceEntity serviceEntity(String serviceRef, String serviceKind, String deploymentMode) {
        PlatformManagedInferenceServiceEntity entity = new PlatformManagedInferenceServiceEntity();
        entity.setId("pmis-" + serviceRef);
        entity.setServiceRef(serviceRef);
        entity.setDisplayName(serviceRef);
        entity.setServiceKind(serviceKind);
        entity.setDeploymentMode(deploymentMode);
        entity.setProviderType("openai");
        entity.setProtocolType("OPENAI_COMPATIBLE");
        entity.setModelId("text-embedding-3-small");
        entity.setEnvironmentScope("dev");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private PlatformManagedInferenceEndpointEntity endpointEntity(String serviceId, String profileRef, String purpose) {
        PlatformManagedInferenceEndpointEntity endpoint = new PlatformManagedInferenceEndpointEntity();
        endpoint.setId("pmie-" + profileRef);
        endpoint.setServiceId(serviceId);
        endpoint.setProfileRef(profileRef);
        endpoint.setEndpointPurpose(purpose);
        endpoint.setDisplayName(profileRef);
        endpoint.setProviderType("openai");
        endpoint.setProtocolType("OPENAI_COMPATIBLE");
        endpoint.setCreatedAt(Instant.now());
        endpoint.setUpdatedAt(Instant.now());
        return endpoint;
    }

    private PlatformManagedInferenceServiceSummary serviceSummary(PlatformManagedInferenceServiceEntity entity) {
        return new PlatformManagedInferenceServiceSummary(
            entity.getId(),
            entity.getServiceRef(),
            entity.getDisplayName(),
            entity.getServiceKind(),
            entity.getDeploymentMode(),
            entity.getProviderType(),
            entity.getProtocolType(),
            entity.getModelId(),
            entity.getEnvironmentScope(),
            entity.getTierScope(),
            entity.getDeploymentId(),
            entity.getRailwayProjectId(),
            entity.getRailwayEnvironmentId(),
            entity.getRailwayServiceId(),
            entity.getDesiredReplicas(),
            entity.getActualReplicas(),
            entity.getMinReplicas(),
            entity.getMaxReplicas(),
            entity.getAutoscalingMode(),
            entity.getBaseUrl(),
            entity.getPrivateNetworkUrl(),
            entity.getHealthPath(),
            entity.getSecretName(),
            entity.getStatus(),
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0,
            0,
            List.of()
        );
    }
}
