package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TenantScopedVectorHandleResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final TenantScopedVectorHandleResolver resolver = new TenantScopedVectorHandleResolver();

    @Test
    void everySharedBackendScopesByCustomerTenantDeploymentAndTargetWithoutChangingSourceConfig() {
        for (String strategy : new String[]{"pinecone", "qdrant", "milvus", "weaviate"}) {
            var provider = mapper.createObjectNode().put("vectorStoragePosture", "SHARED").put("vectorStrategy", strategy);
            var stage = resolver.targetScopedProviderConfig(deployment("customer-a", "dep-a"), provider, "stage");
            var production = resolver.targetScopedProviderConfig(deployment("customer-a", "dep-a"), provider, "production");
            var otherDeployment = resolver.targetScopedProviderConfig(deployment("customer-a", "dep-b"), provider, "stage");
            var otherCustomer = resolver.targetScopedProviderConfig(deployment("customer-b", "dep-a"), provider, "stage");
            assertThat(stage).isNotEqualTo(production).isNotEqualTo(otherDeployment).isNotEqualTo(otherCustomer);
            assertThat(stage).isEqualTo(resolver.targetScopedProviderConfig(deployment("customer-a", "dep-a"), provider, "stage"));
            assertThat(provider.size()).isEqualTo(2);
        }
    }

    @Test
    void weaviateTenantFitsProviderLimitWithoutTruncatingTheTargetIdentityHash() {
        var deployment = deployment("customer-a", "dep-a");
        var provider = mapper.createObjectNode().put("vectorStoragePosture", "SHARED").put("vectorStrategy", "weaviate");
        var bound = resolver.targetScopedProviderConfig(deployment, provider, "stage");
        var identity = mapper.createObjectNode().put("customer", "customer-a").put("tenant", "tenant-a")
            .put("deployment", "dep-a").put("target", "stage");
        var expectedHash = DeploymentEvidenceFingerprint.hash(mapper, identity);
        assertThat(bound.path("weaviateTenantName").asText()).isEqualTo(expectedHash).hasSize(64)
            .matches("[a-f0-9]{64}");
        assertThat(resolver.resolveWeaviateTenantName(deployment, bound)).isEqualTo(expectedHash);
        var otherTenant = deployment("customer-a", "dep-a");
        otherTenant.setTenantId("tenant-b");
        assertThat(resolver.targetScopedProviderConfig(otherTenant, provider, "stage").path("weaviateTenantName").asText())
            .isNotEqualTo(expectedHash);
    }

    @Test
    void sharedStorageNeverInfersTargetOrOwner() {
        var provider = mapper.createObjectNode().put("vectorStoragePosture", "SHARED").put("vectorStrategy", "pinecone");
        assertThatThrownBy(() -> resolver.targetScopedProviderConfig(deployment("customer-a", "dep-a"), provider, null))
            .hasMessageContaining("explicit customer, tenant, deployment and target");
        assertThatThrownBy(() -> resolver.targetScopedProviderConfig(deployment(null, "dep-a"), provider, "stage"))
            .hasMessageContaining("explicit customer");
    }

    @Test
    void separatelyIsolatedEmbeddedStorageDoesNotReceiveAnExternalNamespace() {
        var provider = mapper.createObjectNode().put("vectorStoragePosture", "EMBEDDED").put("vectorStrategy", "lucene");
        assertThat(resolver.targetScopedProviderConfig(deployment("customer-a", "dep-a"), provider, "stage")).isSameAs(provider);
    }

    private DeploymentEntity deployment(String customer, String id) {
        var value = new DeploymentEntity(); value.setCustomerId(customer); value.setTenantId("tenant-a"); value.setId(id); return value;
    }
}
