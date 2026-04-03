package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentTenantScopedVectorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildSharedPineconeSummaryDerivesTenantScopedNamespaceHandle() throws Exception {
        PlatformCustomerTenantService tenantService = mock(PlatformCustomerTenantService.class);
        DeploymentTenantScopedVectorRegistryService registryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        DeploymentEntity deployment = deployment("cust-acme", "ten-retail");
        when(tenantService.summarizeBinding(deployment)).thenReturn(
            new DeploymentTenantBindingSummary(
                "cust-acme",
                "Acme Corp",
                "acme",
                "ACTIVE",
                false,
                "ten-retail",
                "Retail",
                "retail",
                "ACTIVE",
                false,
                false,
                2,
                1,
                "MIGRATION_REQUIRED",
                "migration required"
            )
        );
        when(registryService.summarizeForDeployment(any(), any())).thenReturn(registrySummary("WARNING"));

        DeploymentTenantScopedVectorService service = new DeploymentTenantScopedVectorService(
            tenantService,
            new TenantScopedVectorHandleResolver(),
            registryService
        );

        DeploymentTenantScopedVectorSummary summary = service.build(
            deployment,
            objectMapper.readTree("""
                {
                  "vectorStrategy": "pinecone",
                  "vectorProvisioningMode": "EXTERNAL_EXISTING",
                  "vectorStoragePosture": "SHARED",
                  "pineconeIndexName": "shared-index"
                }
                """)
        );

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.sharedStorage()).isTrue();
        assertThat(summary.scopeType()).isEqualTo("NAMESPACE_PREFIX");
        assertThat(summary.rootResourceValue()).isEqualTo("shared-index");
        assertThat(summary.scopePrefix()).isEqualTo("cust-acme--ten-retail");
        assertThat(summary.scopePattern()).isEqualTo("cust-acme--ten-retail__<entity-type>");
        assertThat(summary.migrationLocked()).isTrue();
        assertThat(summary.registry()).isNotNull();
        assertThat(summary.registry().status()).isEqualTo("WARNING");
    }

    @Test
    void buildSharedWeaviateSummaryBlocksWithoutNativeMultiTenancy() throws Exception {
        PlatformCustomerTenantService tenantService = mock(PlatformCustomerTenantService.class);
        DeploymentTenantScopedVectorRegistryService registryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        DeploymentEntity deployment = deployment("cust-acme", "ten-support");
        when(tenantService.summarizeBinding(deployment)).thenReturn(
            new DeploymentTenantBindingSummary(
                "cust-acme",
                "Acme Corp",
                "acme",
                "ACTIVE",
                false,
                "ten-support",
                "Support",
                "support",
                "ACTIVE",
                false,
                true,
                0,
                0,
                "EDITABLE",
                "editable"
            )
        );
        when(registryService.summarizeForDeployment(any(), any())).thenReturn(registrySummary("BLOCKED"));

        DeploymentTenantScopedVectorService service = new DeploymentTenantScopedVectorService(
            tenantService,
            new TenantScopedVectorHandleResolver(),
            registryService
        );

        DeploymentTenantScopedVectorSummary summary = service.build(
            deployment,
            objectMapper.readTree("""
                {
                  "vectorStrategy": "weaviate",
                  "vectorProvisioningMode": "EXTERNAL_EXISTING",
                  "vectorStoragePosture": "SHARED",
                  "weaviateHost": "tenant.weaviate.local",
                  "weaviateNativeMultiTenancyEnabled": false
                }
                """)
        );

        assertThat(summary.status()).isEqualTo("BLOCKED");
        assertThat(summary.summaryMessage()).contains("native multi-tenancy");
        assertThat(summary.migrationLocked()).isFalse();
        assertThat(summary.registry()).isNotNull();
        assertThat(summary.registry().status()).isEqualTo("BLOCKED");
    }

    @Test
    void buildSharedWeaviateSummaryReportsNormalizedClassPrefixAndTenantHandle() throws Exception {
        PlatformCustomerTenantService tenantService = mock(PlatformCustomerTenantService.class);
        DeploymentTenantScopedVectorRegistryService registryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        DeploymentEntity deployment = deployment("cust-acme", "ten-support");
        when(tenantService.summarizeBinding(deployment)).thenReturn(
            new DeploymentTenantBindingSummary(
                "cust-acme",
                "Acme Corp",
                "acme",
                "ACTIVE",
                false,
                "ten-support",
                "Support",
                "support",
                "ACTIVE",
                false,
                true,
                0,
                0,
                "EDITABLE",
                "editable"
            )
        );
        when(registryService.summarizeForDeployment(any(), any())).thenReturn(registrySummary("READY"));

        DeploymentTenantScopedVectorService service = new DeploymentTenantScopedVectorService(
            tenantService,
            new TenantScopedVectorHandleResolver(),
            registryService
        );

        DeploymentTenantScopedVectorSummary summary = service.build(
            deployment,
            objectMapper.readTree("""
                {
                  "vectorStrategy": "weaviate",
                  "vectorProvisioningMode": "EXTERNAL_EXISTING",
                  "vectorStoragePosture": "SHARED",
                  "weaviateHost": "tenant.weaviate.local",
                  "weaviateNativeMultiTenancyEnabled": true,
                  "weaviateClassPrefix": "customer_acme_",
                  "weaviateTenantName": "tenant-retail"
                }
                """)
        );

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.scopeType()).isEqualTo("CLASS_AND_TENANT");
        assertThat(summary.scopePrefix()).startsWith("CustomerAcme_");
        assertThat(summary.tenantHandle()).isEqualTo("tenant-retail");
        assertThat(summary.scopePattern()).contains("@ tenant tenant-retail");
    }

    @Test
    void buildDedicatedSummaryReportsNonSharedLifecycle() throws Exception {
        PlatformCustomerTenantService tenantService = mock(PlatformCustomerTenantService.class);
        DeploymentTenantScopedVectorRegistryService registryService = mock(DeploymentTenantScopedVectorRegistryService.class);
        DeploymentEntity deployment = deployment("cust-acme", "ten-retail");
        when(tenantService.summarizeBinding(deployment)).thenReturn(
            new DeploymentTenantBindingSummary(
                "cust-acme",
                "Acme Corp",
                "acme",
                "ACTIVE",
                false,
                "ten-retail",
                "Retail",
                "retail",
                "ACTIVE",
                false,
                true,
                0,
                0,
                "EDITABLE",
                "editable"
            )
        );
        when(registryService.summarizeForDeployment(any(), any())).thenReturn(registrySummary("INFO"));

        DeploymentTenantScopedVectorService service = new DeploymentTenantScopedVectorService(
            tenantService,
            new TenantScopedVectorHandleResolver(),
            registryService
        );

        DeploymentTenantScopedVectorSummary summary = service.build(
            deployment,
            objectMapper.readTree("""
                {
                  "vectorStrategy": "qdrant",
                  "vectorProvisioningMode": "EXTERNAL_EXISTING",
                  "vectorStoragePosture": "DEDICATED"
                }
                """)
        );

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.sharedStorage()).isFalse();
        assertThat(summary.scopeType()).isEqualTo("DEDICATED_RESOURCE");
        assertThat(summary.lifecycleOwner()).isEqualTo("CUSTOMER_MANAGED_RESOURCE");
        assertThat(summary.registry()).isNotNull();
        assertThat(summary.registry().status()).isEqualTo("INFO");
    }

    private DeploymentEntity deployment(String customerId, String tenantId) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-12345678");
        deployment.setName("Shared commerce");
        deployment.setEnvironmentName("dev");
        deployment.setCustomerId(customerId);
        deployment.setTenantId(tenantId);
        return deployment;
    }

    private DeploymentTenantScopedVectorRegistrySummary registrySummary(String status) {
        return new DeploymentTenantScopedVectorRegistrySummary(
            status,
            "tsv-12345678",
            "INFO".equals(status) ? 0 : 1,
            "INFO".equals(status) ? 0 : 1,
            Instant.parse("2026-04-03T10:15:30Z"),
            "INFO",
            "Cleanup info",
            "Registry summary"
        );
    }
}
