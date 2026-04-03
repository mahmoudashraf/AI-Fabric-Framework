package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentHostedVerificationContextServiceTest {

    @Test
    void canonicalRolloutUsesCanonicalProfileEvenWhenOperatorRequestsVector() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-pinecone");
        deployment.setName("OpenAI Pinecone Verification");
        deployment.setEnvironmentName("dev");
        deployment.setActiveVersionId("ver-1");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.example");
        deployment.setCustomerId("cus-1");
        deployment.setTenantId("ten-1");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setDeploymentId("dep-pinecone");
        release.setDeploymentVersionId("ver-1");
        release.setStatus("APPLIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-pinecone");
        version.setProviderConfigJson("""
            {"vectorStrategy":"pinecone"}
            """);
        version.setEntityConfigJson("""
            {"ai-config":{"vector-dimensions":1536},"ai-entities":{"product":{},"policy":{},"review":{}}}
            """);
        version.setRoutingConfigJson("""
            {"connector":{"upstream":{"base-url":"https://store.example"}}}
            """);

        when(deploymentRepository.findById(eq("dep-pinecone"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-pinecone"), eq("ver-1")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-1"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-pinecone"))).thenReturn("ecommerce");
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "DEDICATED",
                false,
                "CUSTOMER_PROVIDER",
                "cus-1",
                "Customer One",
                "ten-1",
                "Tenant One",
                "DEDICATED_RESOURCE",
                null,
                null,
                null,
                null,
                null,
                true,
                "migration-locked",
                "provider-owned",
                new DeploymentTenantScopedVectorRegistrySummary("INFO", null, 0, 0, null, "INFO", "Not applicable.", "Not shared."),
                "Dedicated scope"
            )
        );

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-pinecone", "vector", false);

        assertThat(context.profile()).isEqualTo("ecommerce");
        assertThat(context.script()).isEqualTo("scripts/verify-ecommerce-deployment.sh");
        assertThat(context.env()).containsEntry("STORE_BASE_URL", "https://store.example");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_SHARED", "false");
        assertThat(context.env()).doesNotContainKey("EXPECTED_VECTOR_DB");
    }

    @Test
    void sharedVectorDeploymentPublishesTenantScopedVerificationExpectations() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-shared");
        deployment.setActiveVersionId("ver-shared");
        deployment.setRuntimeBaseUrl("https://runtime.shared");
        deployment.setConnectorBaseUrl("https://connector.shared");
        deployment.setCustomerId("cus-shared");
        deployment.setTenantId("ten-shared");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-shared");
        release.setDeploymentId("dep-shared");
        release.setDeploymentVersionId("ver-shared");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-shared");
        version.setProviderConfigJson("""
            {"vectorStrategy":"pinecone","vectorStoragePosture":"SHARED","vectorProvisioningMode":"EXTERNAL_EXISTING"}
            """);
        version.setEntityConfigJson("""
            {"ai-config":{"vector-dimensions":1536},"ai-entities":{"product":{},"policy":{},"review":{}}}
            """);
        version.setRoutingConfigJson("{}");

        when(deploymentRepository.findById(eq("dep-shared"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-shared"), eq("ver-shared")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-shared"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-shared"))).thenReturn(null);
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "SHARED",
                true,
                "CUSTOMER_PROVIDER",
                "cus-shared",
                "Customer Shared",
                "ten-shared",
                "Tenant Shared",
                "NAMESPACE_PREFIX",
                "Index",
                "shared-index",
                "cus-shared--ten-shared",
                null,
                "cus-shared--ten-shared__<entity-type>",
                false,
                "editable",
                "provider-owned",
                new DeploymentTenantScopedVectorRegistrySummary("READY", "tsv-123", 1, 0, null, "BLOCKED", "Still active.", "Registered."),
                "Shared scope ready"
            )
        );

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-shared", "vector", false);

        assertThat(context.profile()).isEqualTo("vector");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_SHARED", "true");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_STATUS", "READY");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_CUSTOMER_ID", "cus-shared");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_TENANT_ID", "ten-shared");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_SCOPE_TYPE", "NAMESPACE_PREFIX");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_ROOT_RESOURCE_VALUE", "shared-index");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_SCOPE_PREFIX", "cus-shared--ten-shared");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_SCOPE_PATTERN", "cus-shared--ten-shared__<entity-type>");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_REGISTRY_STATUS", "READY");
        assertThat(context.env()).containsEntry("EXPECT_TENANT_SCOPED_READINESS_STATUS", "READY");
    }
}
