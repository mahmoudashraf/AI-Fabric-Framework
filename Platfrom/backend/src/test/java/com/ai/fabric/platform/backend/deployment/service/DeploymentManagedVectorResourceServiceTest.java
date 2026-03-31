package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorStateSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentManagedVectorResourceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Test
    void syncProvisionedResourcesRecordsManagedPineconeIndex() {
        DeploymentManagedVectorResourceRepository repository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of());

        DeploymentManagedVectorResourceService service = new DeploymentManagedVectorResourceService(
            repository,
            auditService,
            objectMapper
        );

        service.syncProvisionedResources(
            deployment("dep-123"),
            version("ver-123"),
            release("rel-123"),
            new ManagedVectorProvisioningResult(
                objectMapper.createObjectNode()
                    .put("vectorStrategy", "pinecone")
                    .put("vectorProvisioningMode", "PLATFORM_MANAGED")
                    .put("pineconeManagedIndexEnabled", true),
                objectMapper.createObjectNode()
                    .put("enabled", true)
                    .put("vectorStrategy", "pinecone")
                    .put("mode", "MANAGED_SERVERLESS_INDEX")
                    .put("indexName", "dep-123")
                    .put("apiHost", "https://dep-123.example.pinecone.io")
                    .put("state", "CREATED")
                    .put("runtimeApiKeySecretName", "MANAGED_PINECONE_API_KEY_DEP_DEP_123")
            )
        );

        verify(repository).saveAll(argThatList(resources ->
            resources.size() == 1
                && "pinecone".equals(resources.get(0).getVendor())
                && "INDEX".equals(resources.get(0).getResourceType())
                && "dep-123".equals(resources.get(0).getResourceName())
                && "ACTIVE".equals(resources.get(0).getResourceStatus())
                && "PLATFORM_MANAGED".equals(resources.get(0).getVectorProvisioningMode())
                && resources.get(0).getSecretReferenceNamesJson().contains("MANAGED_PINECONE_API_KEY_DEP_DEP_123")
        ));
        verify(auditService).record(eq("MANAGED_VECTOR_RESOURCES_SYNCED"), eq("DEPLOYMENT"), eq("dep-123"), org.mockito.ArgumentMatchers.anyMap());
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncProvisionedResourcesDetachesStaleResourcesWhenProvisioningIsNoLongerRequested() {
        DeploymentManagedVectorResourceRepository repository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentManagedVectorResourceEntity existing = new DeploymentManagedVectorResourceEntity();
        existing.setId("mvr-1");
        existing.setDeploymentId("dep-123");
        existing.setVendor("pinecone");
        existing.setResourceType("INDEX");
        existing.setResourceName("dep-123");
        existing.setResourceStatus("ACTIVE");
        existing.setSecretReferenceNamesJson("[]");
        existing.setDetailsJson("{}");
        existing.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        existing.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(existing));

        DeploymentManagedVectorResourceService service = new DeploymentManagedVectorResourceService(
            repository,
            auditService,
            objectMapper
        );

        service.syncProvisionedResources(
            deployment("dep-123"),
            version("ver-124"),
            release("rel-124"),
            new ManagedVectorProvisioningResult(
                objectMapper.createObjectNode()
                    .put("vectorStrategy", "lucene")
                    .put("vectorProvisioningMode", "LOCAL_MANAGED"),
                objectMapper.createObjectNode()
                    .put("enabled", false)
                    .put("vectorStrategy", "lucene")
                    .put("mode", "NONE")
            )
        );

        verify(repository).saveAll(argThatList(resources ->
            resources.size() == 1
                && "DETACHED".equals(resources.get(0).getResourceStatus())
                && "ver-124".equals(resources.get(0).getDeploymentVersionId())
                && "rel-124".equals(resources.get(0).getDeploymentReleaseId())
        ));
        verify(auditService).record(eq("MANAGED_VECTOR_RESOURCES_SYNCED"), eq("DEPLOYMENT"), eq("dep-123"), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void buildStateSummaryRequiresManagedResourcesForActiveManagedDeployment() throws Exception {
        DeploymentManagedVectorResourceRepository repository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        DeploymentManagedVectorResourceEntity existing = new DeploymentManagedVectorResourceEntity();
        existing.setId("mvr-1");
        existing.setDeploymentId("dep-123");
        existing.setDeploymentVersionId("ver-123");
        existing.setVendor("pinecone");
        existing.setVectorStrategy("pinecone");
        existing.setVectorProvisioningMode("PLATFORM_MANAGED");
        existing.setManagedMode("MANAGED_SERVERLESS_INDEX");
        existing.setResourceType("INDEX");
        existing.setResourceName("dep-123");
        existing.setResourceReference("dep-123");
        existing.setEndpoint("https://dep-123.example.pinecone.io");
        existing.setResourceStatus("ACTIVE");
        existing.setProvisioningState("REUSED");
        existing.setSecretReferenceNamesJson("[\"PINECONE_API_KEY\",\"MANAGED_PINECONE_API_KEY_DEP_DEP_123\"]");
        existing.setDetailsJson("{\"indexName\":\"dep-123\"}");
        existing.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        existing.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of(existing));

        DeploymentManagedVectorResourceService service = new DeploymentManagedVectorResourceService(
            repository,
            auditService,
            objectMapper
        );

        ObjectNode providerConfig = objectMapper.createObjectNode();
        providerConfig.put("vectorStrategy", "pinecone");
        providerConfig.put("vectorProvisioningMode", "PLATFORM_MANAGED");
        providerConfig.put("pineconeManagedIndexEnabled", true);

        DeploymentManagedVectorStateSummary summary = service.buildStateSummary("dep-123", providerConfig, "ver-123");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.managedRequested()).isTrue();
        assertThat(summary.activeResourceCount()).isEqualTo(1);
        assertThat(summary.resources()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void syncProvisionedResourcesRecordsManagedQdrantCloudResources() {
        DeploymentManagedVectorResourceRepository repository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        when(repository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123")).thenReturn(List.of());

        DeploymentManagedVectorResourceService service = new DeploymentManagedVectorResourceService(
            repository,
            auditService,
            objectMapper
        );

        service.syncProvisionedResources(
            deployment("dep-123"),
            version("ver-123"),
            release("rel-123"),
            new ManagedVectorProvisioningResult(
                objectMapper.createObjectNode()
                    .put("vectorStrategy", "qdrant")
                    .put("vectorProvisioningMode", "PLATFORM_MANAGED"),
                objectMapper.createObjectNode()
                    .put("enabled", true)
                    .put("vectorStrategy", "qdrant")
                    .put("mode", "MANAGED_CLOUD_CLUSTER")
                    .put("clusterId", "cluster-1")
                    .put("clusterName", "aifabric-123")
                    .put("baseUrl", "https://cluster.example")
                    .put("clusterState", "CREATED")
                    .put("databaseApiKeyId", "key-1")
                    .put("databaseApiKeyName", "ai-fabric-123")
                    .put("databaseApiKeySecretName", "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123")
                    .set("collections", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("name", "product").put("state", "CREATED"))
                        .add(objectMapper.createObjectNode().put("name", "policy").put("state", "CREATED")))
            )
        );

        verify(repository).saveAll(argThatList(resources ->
            resources.size() == 4
                && resources.stream().anyMatch(resource ->
                    "CLUSTER".equals(resource.getResourceType()) && "qdrant".equals(resource.getVendor()))
                && resources.stream().anyMatch(resource ->
                    "DATABASE_API_KEY".equals(resource.getResourceType())
                        && resource.getSecretReferenceNamesJson().contains("MANAGED_QDRANT_DB_API_KEY_DEP_DEP_123"))
                && resources.stream().filter(resource -> "COLLECTION".equals(resource.getResourceType())).count() == 2
        ));
    }

    private DeploymentEntity deployment(String id) {
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId(id);
        entity.setName("Deployment");
        entity.setEnvironmentName("dev");
        entity.setTemplateId("dev-openai-lucene");
        entity.setStatus("VERSION_PUBLISHED");
        entity.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        return entity;
    }

    private DeploymentVersionEntity version(String id) {
        DeploymentVersionEntity entity = new DeploymentVersionEntity();
        entity.setId(id);
        entity.setDeploymentId("dep-123");
        entity.setVersionLabel("v1");
        entity.setConfigHash("hash");
        entity.setProviderConfigJson("{}");
        entity.setEntityConfigJson("{}");
        entity.setPublishedAt(Instant.parse("2026-03-31T00:00:00Z"));
        return entity;
    }

    private DeploymentReleaseEntity release(String id) {
        DeploymentReleaseEntity entity = new DeploymentReleaseEntity();
        entity.setId(id);
        entity.setDeploymentId("dep-123");
        entity.setDeploymentVersionId("ver-123");
        entity.setStatus("PROVISIONING");
        entity.setVerificationStatus("PENDING");
        entity.setProvisioningStatus("RUNNING");
        entity.setProvisioningTarget("RAILWAY_API");
        entity.setProvisioningDetailsJson("{}");
        entity.setCreatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        entity.setAppliedAt(Instant.parse("2026-03-31T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-03-31T00:00:00Z"));
        return entity;
    }

    @SuppressWarnings("SameParameterValue")
    private List<DeploymentManagedVectorResourceEntity> argThatList(java.util.function.Predicate<List<DeploymentManagedVectorResourceEntity>> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
