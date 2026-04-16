package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformInferenceProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceEndpointSummary;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceServiceSummary;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformManagedInferenceProvisioningServiceTest {

    @Test
    void reconcileSharedEmbeddingServiceProvisionsRailwayAndActivatesEndpoint() {
        PlatformManagedInferenceServiceEntity serviceEntity = sharedEmbeddingService(2);
        PlatformManagedInferenceEndpointEntity endpointEntity = sharedEmbeddingEndpoint(serviceEntity.getId());

        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformManagedInferenceServiceRepository serviceRepository = mock(PlatformManagedInferenceServiceRepository.class);
        PlatformManagedInferenceEndpointRepository endpointRepository = mock(PlatformManagedInferenceEndpointRepository.class);
        PlatformManagedInferenceServiceService serviceService = mock(PlatformManagedInferenceServiceService.class);

        when(serviceService.requireService("shared-embeddings-standard")).thenReturn(serviceEntity);
        when(serviceService.getService("shared-embeddings-standard")).thenReturn(summary(serviceEntity, endpointEntity));
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.findAllByServiceIdOrderByProfileRefAsc(serviceEntity.getId())).thenReturn(List.of(endpointEntity));

        when(railwayGraphqlClient.findProjectByName("workspace", "loom-inference-dev-shared-embeddings-standard"))
            .thenReturn(null);
        when(railwayGraphqlClient.createProject("workspace", "loom-inference-dev-shared-embeddings-standard", "dev"))
            .thenReturn(new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "loom-inference-dev-shared-embeddings-standard",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "dev")),
                List.of()
            ));
        when(railwayGraphqlClient.createServiceFromRepository(
            "proj-1",
            "shared-embeddings-shared-embeddings-standard",
            "mahmoudashraf/AI-Fabric-Framework",
            "main"
        )).thenReturn(new RailwayGraphqlClient.RailwayServiceSummary("svc-1", "shared-embeddings-shared-embeddings-standard"));
        when(railwayGraphqlClient.hasStagedChanges("env-1")).thenReturn(true);
        when(railwayGraphqlClient.deployService("svc-1", "env-1")).thenReturn("dep-1");
        when(railwayGraphqlClient.getDeployment("dep-1")).thenReturn(
            new RailwayGraphqlClient.RailwayDeploymentSummary("dep-1", "SUCCESS", null, null, Instant.now().toString())
        );
        when(railwayGraphqlClient.getServiceInstance("env-1", "svc-1")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-1",
                "svc-1",
                "shared-embeddings-shared-embeddings-standard",
                ".",
                "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile",
                "/actuator/health",
                "http://shared-embeddings.internal",
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.listServiceDomains("proj-1", "env-1", "svc-1")).thenReturn(List.of());
        when(railwayGraphqlClient.createServiceDomain("svc-1", "env-1"))
            .thenReturn(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-1", "shared-embeddings.example.com"));

        when(platformSecretService.isSecretPresent(serviceEntity.getSecretName())).thenReturn(false);
        doNothing().when(platformSecretService).upsertManagedSecret(eq(serviceEntity.getSecretName()), any(), any());
        when(platformSecretService.resolveSecret(serviceEntity.getSecretName())).thenReturn("secret-value");

        PlatformManagedInferenceProvisioningService service = new PlatformManagedInferenceProvisioningService(
            provisioningProperties(),
            inferenceProvisioningProperties(),
            railwayGraphqlClient,
            platformSecretService,
            serviceRepository,
            endpointRepository,
            serviceService,
            new ObjectMapper()
        );

        PlatformManagedInferenceServiceSummary result = service.reconcile("shared-embeddings-standard");

        assertThat(result.serviceRef()).isEqualTo("shared-embeddings-standard");
        assertThat(serviceEntity.getStatus()).isEqualTo("ACTIVE");
        assertThat(serviceEntity.getBaseUrl()).isEqualTo("https://shared-embeddings.example.com");
        assertThat(serviceEntity.getActualReplicas()).isEqualTo(2);
        assertThat(endpointEntity.getStatus()).isEqualTo("ACTIVE");
        assertThat(endpointEntity.getBaseUrl()).isEqualTo("https://shared-embeddings.example.com/v1");
        verify(railwayGraphqlClient).updateServiceInstance(
            "svc-1",
            "env-1",
            ".",
            "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile",
            "/actuator/health",
            2
        );
        verify(railwayGraphqlClient).upsertVariables(
            eq("proj-1"),
            eq("env-1"),
            eq("svc-1"),
            argThat(env -> env.stream().anyMatch(item -> "AI_FABRIC_EMBEDDING_WORKER_API_KEY".equals(item.name())))
        );
    }

    @Test
    void scaleSharedEmbeddingServiceAppliesUpdatedReplicaTarget() {
        PlatformManagedInferenceServiceEntity serviceEntity = sharedEmbeddingService(3);
        PlatformManagedInferenceEndpointEntity endpointEntity = sharedEmbeddingEndpoint(serviceEntity.getId());

        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformManagedInferenceServiceRepository serviceRepository = mock(PlatformManagedInferenceServiceRepository.class);
        PlatformManagedInferenceEndpointRepository endpointRepository = mock(PlatformManagedInferenceEndpointRepository.class);
        PlatformManagedInferenceServiceService serviceService = mock(PlatformManagedInferenceServiceService.class);

        when(serviceService.requireService("shared-embeddings-standard")).thenReturn(serviceEntity);
        when(serviceService.updateDesiredReplicas("shared-embeddings-standard", 3)).thenReturn(summary(serviceEntity, endpointEntity));
        when(serviceService.getService("shared-embeddings-standard")).thenReturn(summary(serviceEntity, endpointEntity));
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(endpointRepository.findAllByServiceIdOrderByProfileRefAsc(serviceEntity.getId())).thenReturn(List.of(endpointEntity));

        when(railwayGraphqlClient.findProjectByName("workspace", "loom-inference-dev-shared-embeddings-standard"))
            .thenReturn(new RailwayGraphqlClient.RailwayProjectSnapshot(
                "proj-1",
                "loom-inference-dev-shared-embeddings-standard",
                List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-1", "dev")),
                List.of(new RailwayGraphqlClient.RailwayServiceSummary("svc-1", "shared-embeddings-shared-embeddings-standard"))
            ));
        when(railwayGraphqlClient.hasStagedChanges("env-1")).thenReturn(false);
        when(railwayGraphqlClient.deployService("svc-1", "env-1")).thenReturn("dep-1");
        when(railwayGraphqlClient.getDeployment("dep-1")).thenReturn(
            new RailwayGraphqlClient.RailwayDeploymentSummary("dep-1", "SUCCESS", null, null, Instant.now().toString())
        );
        when(railwayGraphqlClient.getServiceInstance("env-1", "svc-1")).thenReturn(
            new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-1",
                "svc-1",
                "shared-embeddings-shared-embeddings-standard",
                ".",
                "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile",
                "/actuator/health",
                "http://shared-embeddings.internal",
                "mahmoudashraf/AI-Fabric-Framework",
                null
            )
        );
        when(railwayGraphqlClient.listServiceDomains("proj-1", "env-1", "svc-1")).thenReturn(
            List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-1", "shared-embeddings.example.com"))
        );

        when(platformSecretService.isSecretPresent(serviceEntity.getSecretName())).thenReturn(true);
        when(platformSecretService.resolveSecret(serviceEntity.getSecretName())).thenReturn("secret-value");

        PlatformManagedInferenceProvisioningService service = new PlatformManagedInferenceProvisioningService(
            provisioningProperties(),
            inferenceProvisioningProperties(),
            railwayGraphqlClient,
            platformSecretService,
            serviceRepository,
            endpointRepository,
            serviceService,
            new ObjectMapper()
        );

        service.scale("shared-embeddings-standard", 3);

        verify(serviceService).updateDesiredReplicas("shared-embeddings-standard", 3);
        verify(railwayGraphqlClient).updateServiceInstance(
            "svc-1",
            "env-1",
            ".",
            "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile",
            "/actuator/health",
            3
        );
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_API",
            "https://backboard.railway.com/graphql/v2",
            "token",
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "dev",
            "workspace",
            "ai-infrastructure-module/ai-fabric-runtime",
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector",
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile",
            "runtime",
            "rest-connector",
            48,
            "",
            "",
            false,
            false,
            60_000,
            Duration.ofSeconds(1),
            Duration.ofMinutes(2)
        );
    }

    private PlatformInferenceProvisioningProperties inferenceProvisioningProperties() {
        return new PlatformInferenceProvisioningProperties(
            "loom-inference",
            ".",
            "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile",
            "shared-embeddings",
            "/actuator/health",
            "ai-fabric-product/ai-fabric-shared-ollama-service",
            "ai-fabric-product/ai-fabric-shared-ollama-service/deploy/railway/Dockerfile",
            "shared-ollama",
            "/api/tags",
            ".",
            "ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile",
            "embedding-worker",
            "/actuator/health",
            Duration.ofSeconds(1),
            Duration.ofMinutes(2)
        );
    }

    private PlatformManagedInferenceServiceEntity sharedEmbeddingService(int desiredReplicas) {
        PlatformManagedInferenceServiceEntity entity = new PlatformManagedInferenceServiceEntity();
        entity.setId("pmis-shared-embeddings-standard");
        entity.setServiceRef("shared-embeddings-standard");
        entity.setDisplayName("Shared Embeddings Standard");
        entity.setServiceKind("SHARED_EMBEDDING_SERVICE");
        entity.setDeploymentMode("SHARED_PLATFORM_SERVICE");
        entity.setProviderType("openai");
        entity.setProtocolType("OPENAI_COMPATIBLE");
        entity.setModelId("bge-small-en-v1.5");
        entity.setEnvironmentScope("dev");
        entity.setTierScope("standard");
        entity.setDesiredReplicas(desiredReplicas);
        entity.setMinReplicas(1);
        entity.setMaxReplicas(6);
        entity.setAutoscalingMode("MANUAL");
        entity.setHealthPath("/actuator/health");
        entity.setSecretName("MANAGED_SHARED_INFERENCE_SHARED_EMBEDDINGS_STANDARD_API_KEY");
        entity.setStatus("CREATED");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private PlatformManagedInferenceEndpointEntity sharedEmbeddingEndpoint(String serviceId) {
        PlatformManagedInferenceEndpointEntity endpoint = new PlatformManagedInferenceEndpointEntity();
        endpoint.setId("pmie-shared-embeddings-standard");
        endpoint.setProfileRef("shared-embeddings-standard");
        endpoint.setServiceId(serviceId);
        endpoint.setEndpointPurpose("EMBEDDING");
        endpoint.setDisplayName("Shared Embeddings Standard");
        endpoint.setProviderType("openai");
        endpoint.setProtocolType("OPENAI_COMPATIBLE");
        endpoint.setStatus("CREATED");
        endpoint.setCreatedAt(Instant.now());
        endpoint.setUpdatedAt(Instant.now());
        return endpoint;
    }

    private PlatformManagedInferenceServiceSummary summary(PlatformManagedInferenceServiceEntity entity,
                                                           PlatformManagedInferenceEndpointEntity endpoint) {
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
            List.of(new PlatformManagedInferenceEndpointSummary(
                endpoint.getId(),
                endpoint.getProfileRef(),
                endpoint.getServiceId(),
                endpoint.getEndpointPurpose(),
                endpoint.getDisplayName(),
                endpoint.getProviderType(),
                endpoint.getProtocolType(),
                endpoint.getBaseUrl(),
                endpoint.getDeploymentName(),
                endpoint.getApiVersion(),
                endpoint.getSecretName(),
                endpoint.getStatus()
            ))
        );
    }
}
