package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationContextSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanRevisionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

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
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(notConfiguredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-pinecone", "vector", false);

        assertThat(context.profile()).isEqualTo("ecommerce");
        assertThat(context.script()).isEqualTo("scripts/verify-ecommerce-deployment.sh");
        assertThat(context.env()).containsEntry("VERIFY_PLATFORM_USER_DIRECTORY_ADMIN", "true");
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
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

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
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(notConfiguredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-shared", "vector", false);

        assertThat(context.profile()).isEqualTo("vector");
        assertThat(context.env()).containsEntry("VERIFY_PLATFORM_USER_DIRECTORY_ADMIN", "true");
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

    @Test
    void configuredVectorizationDeploymentPublishesVectorizationVerificationExpectations() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-vectorization");
        deployment.setActiveVersionId("ver-vectorization");
        deployment.setRuntimeBaseUrl("https://runtime.vectorization");
        deployment.setConnectorBaseUrl("https://connector.vectorization");
        deployment.setCustomerId("cus-vectorization");
        deployment.setTenantId("ten-vectorization");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-vectorization");
        release.setDeploymentId("dep-vectorization");
        release.setDeploymentVersionId("ver-vectorization");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-vectorization");
        version.setEntityConfigJson("""
            {"ai-entities":{"product":{},"policy":{},"review":{}}}
            """);
        version.setRoutingConfigJson("{}");
        version.setProviderConfigJson("""
            {"vectorStrategy":"pinecone"}
            """);

        when(deploymentRepository.findById(eq("dep-vectorization"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-vectorization"), eq("ver-vectorization")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-vectorization"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-vectorization"))).thenReturn("vector");
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "DEDICATED",
                false,
                "CUSTOMER_PROVIDER",
                "cus-vectorization",
                "Customer Vectorization",
                "ten-vectorization",
                "Tenant Vectorization",
                "DEDICATED_RESOURCE",
                null,
                null,
                null,
                null,
                null,
                false,
                "editable",
                "provider-owned",
                null,
                "Dedicated vector scope."
            )
        );
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(configuredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-vectorization", "vector", false);
        DeploymentHostedVerificationContextSummary writableContext = service.buildContextForOperator("dep-vectorization", "vector", true);

        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_PLAN_PRESENT", "true");
        assertThat(context.env()).containsEntry("VERIFY_PLATFORM_USER_DIRECTORY_ADMIN", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_SOURCE_CONNECTION_PRESENT", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_ACTIVE_REVISION_PRESENT", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_CONFIGURED", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_RUNNER_PRESENT", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_RUNNER_REQUIRED", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_PLATFORM_MANAGED_RUNNER", "true");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_RUNNER_MODE", "PLATFORM_MANAGED_AUTO");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_SYNC_STATE", "IN_SYNC");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_SOURCE_ADAPTER", "REST_API");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_SOURCE_AUTH_MODE", "API_KEY");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_RUNNER_STATUS", "ACTIVE");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_RUNNER_COMPATIBILITY_STATUS", "CURRENT");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_ENTITY_SCOPE", "policy,product");
        assertThat(context.env()).containsEntry("EXPECT_VECTORIZATION_AVAILABLE_ENTITIES", "policy,product,review");
        assertThat(context.env()).containsEntry("VERIFY_VECTORIZATION_ADMIN", "true");
        assertThat(context.env()).containsEntry("VERIFY_VECTORIZATION_RUNNER_ACTIVE", "true");
        assertThat(context.env()).containsEntry("VERIFY_VECTORIZATION_SAMPLE", "false");
        assertThat(context.env()).containsEntry("VERIFY_TENANT_SHARED_ISOLATION", "false");
        assertThat(writableContext.env()).containsEntry("VERIFY_VECTORIZATION_SAMPLE", "true");
    }

    @Test
    void vectorHostedVerificationDoesNotRequireConnectorUrlWhenRuntimeIsPresent() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-runtime-only-vector");
        deployment.setActiveVersionId("ver-runtime-only-vector");
        deployment.setRuntimeBaseUrl("https://runtime.vector-only");
        deployment.setConnectorBaseUrl(null);

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-runtime-only-vector");
        release.setDeploymentId("dep-runtime-only-vector");
        release.setDeploymentVersionId("ver-runtime-only-vector");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-runtime-only-vector");
        version.setProviderConfigJson("""
            {"vectorStrategy":"pinecone"}
            """);
        version.setEntityConfigJson("""
            {"ai-entities":{"product":{},"review":{}}}
            """);
        version.setRoutingConfigJson("{}");

        when(deploymentRepository.findById(eq("dep-runtime-only-vector"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-runtime-only-vector"), eq("ver-runtime-only-vector")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-runtime-only-vector"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-runtime-only-vector"))).thenReturn("vector");
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "DEDICATED",
                false,
                "CUSTOMER_PROVIDER",
                null,
                null,
                null,
                null,
                "DEDICATED_RESOURCE",
                null,
                null,
                null,
                null,
                null,
                false,
                "editable",
                "provider-owned",
                null,
                "Dedicated vector scope."
            )
        );
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(notConfiguredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-runtime-only-vector", "vector", false);

        assertThat(context.script()).isEqualTo("scripts/verify-vector-deployment.sh");
        assertThat(context.env()).containsEntry("RUNTIME_BASE_URL", "https://runtime.vector-only");
        assertThat(context.env()).containsEntry("RUNTIME_AUTH_OVERVIEW_URL", "https://runtime.vector-only/api/admin/auth/overview");
        assertThat(context.env()).doesNotContainKeys(
            "RUNTIME_CONNECTOR_HEALTH_URL",
            "RUNTIME_CONNECTOR_OVERVIEW_URL",
            "RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL",
            "RUNTIME_CONNECTOR_READ_PROXY_BASE_URL"
        );
        assertThat(context.env()).doesNotContainKey("REST_CONNECTOR_BASE_URL");
    }

    @Test
    void ecommerceHostedVerificationDoesNotRequireConnectorUrlWhenRuntimeIsPresent() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-runtime-only-ecommerce");
        deployment.setActiveVersionId("ver-runtime-only-ecommerce");
        deployment.setRuntimeBaseUrl("https://runtime.ecommerce-only");
        deployment.setConnectorBaseUrl(null);
        deployment.setCustomerId("cus-ecommerce");
        deployment.setTenantId("ten-ecommerce");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-runtime-only-ecommerce");
        release.setDeploymentId("dep-runtime-only-ecommerce");
        release.setDeploymentVersionId("ver-runtime-only-ecommerce");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-runtime-only-ecommerce");
        version.setProviderConfigJson("""
            {"vectorStrategy":"lucene"}
            """);
        version.setEntityConfigJson("""
            {"ai-entities":{"product":{},"policy":{},"review":{}}}
            """);
        version.setRoutingConfigJson("""
            {"connector":{"upstream":{"base-url":"https://store.example"}}}
            """);

        when(deploymentRepository.findById(eq("dep-runtime-only-ecommerce"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-runtime-only-ecommerce"), eq("ver-runtime-only-ecommerce")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-runtime-only-ecommerce"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-runtime-only-ecommerce"))).thenReturn("ecommerce");
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "lucene",
                "LOCAL_MANAGED",
                "DEDICATED",
                false,
                "PLATFORM_LOCAL",
                "cus-ecommerce",
                "Customer Ecommerce",
                "ten-ecommerce",
                "Tenant Ecommerce",
                "DEDICATED_RESOURCE",
                null,
                null,
                null,
                null,
                null,
                false,
                "editable",
                "platform-owned",
                null,
                "Runtime-only ecommerce verification"
            )
        );
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(notConfiguredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-runtime-only-ecommerce", "ecommerce", false);

        assertThat(context.script()).isEqualTo("scripts/verify-ecommerce-deployment.sh");
        assertThat(context.env()).containsEntry("RUNTIME_BASE_URL", "https://runtime.ecommerce-only");
        assertThat(context.env()).containsEntry("RUNTIME_AUTH_OVERVIEW_URL", "https://runtime.ecommerce-only/api/admin/auth/overview");
        assertThat(context.env()).containsEntry("STORE_BASE_URL", "https://store.example");
        assertThat(context.env()).doesNotContainKeys(
            "RUNTIME_CONNECTOR_HEALTH_URL",
            "RUNTIME_CONNECTOR_OVERVIEW_URL",
            "RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL",
            "RUNTIME_CONNECTOR_READ_PROXY_BASE_URL"
        );
        assertThat(context.env()).doesNotContainKey("REST_CONNECTOR_BASE_URL");
    }

    @Test
    void ecommerceReadOnlyHostedVerificationOmitsConnectorCompatibilityUrlEvenWhenConnectorExists() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-readonly-ecommerce");
        deployment.setActiveVersionId("ver-readonly-ecommerce");
        deployment.setRuntimeBaseUrl("https://runtime.readonly-ecommerce");
        deployment.setConnectorBaseUrl("https://connector.readonly-ecommerce");
        deployment.setCustomerId("cus-readonly");
        deployment.setTenantId("ten-readonly");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-readonly-ecommerce");
        release.setDeploymentId("dep-readonly-ecommerce");
        release.setDeploymentVersionId("ver-readonly-ecommerce");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-readonly-ecommerce");
        version.setProviderConfigJson("""
            {"vectorStrategy":"lucene"}
            """);
        version.setEntityConfigJson("""
            {"ai-entities":{"product":{},"policy":{},"review":{}}}
            """);
        version.setRoutingConfigJson("""
            {"connector":{"upstream":{"base-url":"https://store.example"}}}
            """);

        when(deploymentRepository.findById(eq("dep-readonly-ecommerce"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-readonly-ecommerce"), eq("ver-readonly-ecommerce")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-readonly-ecommerce"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-readonly-ecommerce"))).thenReturn("ecommerce");
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "lucene",
                "LOCAL_MANAGED",
                "DEDICATED",
                false,
                "PLATFORM_LOCAL",
                "cus-readonly",
                "Customer Read Only",
                "ten-readonly",
                "Tenant Read Only",
                "DEDICATED_RESOURCE",
                null,
                null,
                null,
                null,
                null,
                false,
                "editable",
                "platform-owned",
                null,
                "Readonly ecommerce verification"
            )
        );
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(notConfiguredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary readOnlyContext = service.buildContextForOperator("dep-readonly-ecommerce", "ecommerce", false);
        DeploymentHostedVerificationContextSummary writableContext = service.buildContextForOperator("dep-readonly-ecommerce", "ecommerce", true);

        assertThat(readOnlyContext.env()).containsEntry("RUNTIME_BASE_URL", "https://runtime.readonly-ecommerce");
        assertThat(readOnlyContext.env()).containsEntry("RUNTIME_AUTH_OVERVIEW_URL", "https://runtime.readonly-ecommerce/api/admin/auth/overview");
        assertThat(readOnlyContext.env()).containsEntry("RUNTIME_CONNECTOR_HEALTH_URL", "https://runtime.readonly-ecommerce/api/admin/connector/health");
        assertThat(readOnlyContext.env()).containsEntry("RUNTIME_CONNECTOR_OVERVIEW_URL", "https://runtime.readonly-ecommerce/api/admin/connector/overview");
        assertThat(readOnlyContext.env()).containsEntry("RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL", "https://runtime.readonly-ecommerce/api/admin/connector/actions/overview");
        assertThat(readOnlyContext.env()).containsEntry("RUNTIME_CONNECTOR_READ_PROXY_BASE_URL", "https://runtime.readonly-ecommerce/api/admin/connector/proxy");
        assertThat(readOnlyContext.env()).doesNotContainKey("REST_CONNECTOR_BASE_URL");
        assertThat(writableContext.env()).containsEntry("RUNTIME_AUTH_OVERVIEW_URL", "https://runtime.readonly-ecommerce/api/admin/auth/overview");
        assertThat(writableContext.env()).containsEntry("RUNTIME_CONNECTOR_HEALTH_URL", "https://runtime.readonly-ecommerce/api/admin/connector/health");
        assertThat(writableContext.env()).containsEntry("RUNTIME_CONNECTOR_OVERVIEW_URL", "https://runtime.readonly-ecommerce/api/admin/connector/overview");
        assertThat(writableContext.env()).containsEntry("RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL", "https://runtime.readonly-ecommerce/api/admin/connector/actions/overview");
        assertThat(writableContext.env()).containsEntry("RUNTIME_CONNECTOR_READ_PROXY_BASE_URL", "https://runtime.readonly-ecommerce/api/admin/connector/proxy");
        assertThat(writableContext.env()).doesNotContainKey("REST_CONNECTOR_BASE_URL");
    }

    @Test
    void vectorHostedVerificationOmitsConnectorCompatibilityUrlEvenWhenConnectorExists() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentAccessService accessService = mock(DeploymentAccessService.class);
        DeploymentVerificationRolloutService rolloutService = mock(DeploymentVerificationRolloutService.class);
        DeploymentTenantScopedVectorService tenantScopedVectorService = mock(DeploymentTenantScopedVectorService.class);
        DeploymentVectorizationVerificationService vectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-readonly-vector");
        deployment.setActiveVersionId("ver-readonly-vector");
        deployment.setRuntimeBaseUrl("https://runtime.readonly-vector");
        deployment.setConnectorBaseUrl("https://connector.readonly-vector");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-readonly-vector");
        release.setDeploymentId("dep-readonly-vector");
        release.setDeploymentVersionId("ver-readonly-vector");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-readonly-vector");
        version.setProviderConfigJson("""
            {"vectorStrategy":"pinecone"}
            """);
        version.setEntityConfigJson("""
            {"ai-entities":{"product":{},"review":{}}}
            """);
        version.setRoutingConfigJson("{}");

        when(deploymentRepository.findById(eq("dep-readonly-vector"))).thenReturn(Optional.of(deployment));
        when(accessService.requireDeploymentOperatorAccess(eq(deployment))).thenReturn(deployment);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(eq("dep-readonly-vector"), eq("ver-readonly-vector")))
            .thenReturn(Optional.of(release));
        when(versionRepository.findById(eq("ver-readonly-vector"))).thenReturn(Optional.of(version));
        when(rolloutService.canonicalVerificationProfile(eq("dep-readonly-vector"))).thenReturn("vector");
        when(tenantScopedVectorService.build(eq(deployment), any())).thenReturn(
            new DeploymentTenantScopedVectorSummary(
                "READY",
                "pinecone",
                "EXTERNAL_EXISTING",
                "DEDICATED",
                false,
                "CUSTOMER_PROVIDER",
                null,
                null,
                null,
                null,
                "DEDICATED_RESOURCE",
                null,
                null,
                null,
                null,
                null,
                false,
                "editable",
                "provider-owned",
                null,
                "Dedicated vector scope."
            )
        );
        when(vectorizationVerificationService.build(eq(deployment), any())).thenReturn(notConfiguredVectorizationSummary(deployment.getId()));

        DeploymentHostedVerificationContextService service = new DeploymentHostedVerificationContextService(
            deploymentRepository,
            releaseRepository,
            versionRepository,
            accessService,
            rolloutService,
            tenantScopedVectorService,
            vectorizationVerificationService,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofHours(1)),
            new ObjectMapper()
        );

        DeploymentHostedVerificationContextSummary context = service.buildContextForOperator("dep-readonly-vector", "vector", false);

        assertThat(context.env()).containsEntry("RUNTIME_BASE_URL", "https://runtime.readonly-vector");
        assertThat(context.env()).containsEntry("RUNTIME_AUTH_OVERVIEW_URL", "https://runtime.readonly-vector/api/admin/auth/overview");
        assertThat(context.env()).containsEntry("RUNTIME_CONNECTOR_HEALTH_URL", "https://runtime.readonly-vector/api/admin/connector/health");
        assertThat(context.env()).containsEntry("RUNTIME_CONNECTOR_OVERVIEW_URL", "https://runtime.readonly-vector/api/admin/connector/overview");
        assertThat(context.env()).containsEntry("RUNTIME_CONNECTOR_ACTIONS_OVERVIEW_URL", "https://runtime.readonly-vector/api/admin/connector/actions/overview");
        assertThat(context.env()).containsEntry("RUNTIME_CONNECTOR_READ_PROXY_BASE_URL", "https://runtime.readonly-vector/api/admin/connector/proxy");
        assertThat(context.env()).doesNotContainKey("REST_CONNECTOR_BASE_URL");
    }

    private DeploymentVectorizationVerificationSummary notConfiguredVectorizationSummary(String deploymentId) {
        return new DeploymentVectorizationVerificationSummary(
            deploymentId,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            List.of("policy", "product", "review"),
            List.of(),
            null,
            null,
            null
        );
    }

    private DeploymentVectorizationVerificationSummary configuredVectorizationSummary(String deploymentId) {
        return new DeploymentVectorizationVerificationSummary(
            deploymentId,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            List.of("policy", "product", "review"),
            List.of("policy", "product"),
            new VectorizationSourceConnectionSummary(
                "vcn-1",
                deploymentId,
                "Commerce API",
                "REST_API",
                "API_KEY",
                "READY",
                json("{}"),
                json("{}"),
                json("""
                    {"countsByEntityType":{"product":12,"policy":4,"review":1}}
                    """),
                Instant.parse("2026-04-04T00:00:00Z"),
                Instant.parse("2026-04-04T00:05:00Z")
            ),
            new VectorizationPlanSummary(
                "vpl-1",
                deploymentId,
                "Onboarding vectorization",
                "ACTIVE",
                "PLATFORM_MANAGED_AUTO",
                "IN_SYNC",
                List.of("IN_SYNC"),
                json("{}"),
                "hash-1",
                "hash-1",
                "vpr-1",
                "vcn-1",
                "vrn-1",
                "vrn-1",
                null,
                null,
                new VectorizationPlanRevisionSummary(
                    "vpr-1",
                    1,
                    "ACTIVE",
                    "vcn-1",
                    json("""
                        ["policy","product"]
                        """),
                    json("{}"),
                    json("{}"),
                    "hash-1",
                    Instant.parse("2026-04-04T00:00:00Z"),
                    Instant.parse("2026-04-04T00:00:00Z")
                ),
                Instant.parse("2026-04-04T00:00:00Z"),
                Instant.parse("2026-04-04T00:05:00Z")
            ),
            new VectorizationRunnerSummary(
                "vrr-1",
                "PLATFORM_MANAGED_AUTO",
                "ACTIVE",
                "CURRENT",
                "hint-1234",
                Instant.parse("2030-04-05T00:00:00Z"),
                "vectorization-runner-dep-vectorization",
                "2026.04.04",
                "2026.04",
                Instant.parse("2026-04-04T00:02:00Z"),
                Instant.parse("2026-04-04T00:04:00Z"),
                Instant.parse("2026-04-04T01:04:00Z")
            )
        );
    }

    private JsonNode json(String value) {
        try {
            return new ObjectMapper().readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
