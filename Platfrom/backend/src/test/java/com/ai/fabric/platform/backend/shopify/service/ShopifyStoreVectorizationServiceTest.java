package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanRevisionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreVectorizationServiceTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void reconcileInstallsRequiredShopifyDataPluginsAndConfiguresPlan() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        VectorizationService vectorizationService = mock(VectorizationService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("alpha.myshopify.com");
        store.setDeploymentId("dep-123");
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("ten-123");

        VectorizationSourceConnectionSummary connection = new VectorizationSourceConnectionSummary(
            "vcn-123",
            "dep-123",
            "Shopify store alpha.myshopify.com",
            "SHOPIFY-STORE",
            "PRIVATE_RUNTIME_BACKEND_MEDIATED",
            "READY",
            JSON.objectNode(),
            JSON.objectNode(),
            JSON.objectNode(),
            Instant.now(),
            Instant.now()
        );
        VectorizationPlanRevisionSummary revision = new VectorizationPlanRevisionSummary(
            "vpr-123",
            1,
            "ACTIVE",
            "vcn-123",
            JSON.arrayNode().add("product").add("support-policy"),
            JSON.objectNode(),
            JSON.objectNode(),
            "hash-123",
            Instant.now(),
            Instant.now()
        );
        VectorizationPlanSummary plan = new VectorizationPlanSummary(
            "vpl-123",
            "dep-123",
            "Shopify store vectorization",
            "ACTIVE",
            "PLATFORM_MANAGED_AUTO",
            "IN_SYNC",
            List.of(),
            JSON.objectNode(),
            "hash-123",
            "hash-123",
            "vpr-123",
            "vcn-123",
            null,
            null,
            null,
            null,
            revision,
            Instant.now(),
            Instant.now()
        );
        VectorizationOverviewSummary overview = new VectorizationOverviewSummary(
            "dep-123",
            "cus-123",
            "ten-123",
            "ver-123",
            "v1",
            "cfg-123",
            connection,
            plan,
            new VectorizationRunnerSummary(
                "vrr-123",
                "PLATFORM_MANAGED_AUTO",
                "ACTIVE",
                "COMPATIBLE",
                "hint",
                Instant.now().plusSeconds(3600),
                "vectorization-runner-dep-123",
                "2026.04.track-b",
                "1",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(300)
            ),
            List.of()
        );

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.empty());
        when(installService.listInstalls("dep-123"))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED")
            ))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED"),
                install("mpi-catalog", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID, "ENABLED"),
                install("mpi-policies", ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID, "ENABLED")
            ));
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel(any())).thenReturn("1.0.0");
        when(vectorizationService.upsertSourceConnection(eq("dep-123"), any())).thenReturn(connection);
        when(vectorizationService.upsertPlan(eq("dep-123"), any())).thenReturn(plan);
        when(vectorizationService.getOverviewForTrustedCaller(deployment)).thenReturn(overview);

        ShopifyStoreVectorizationService service = service(
            repository,
            deploymentRepository,
            deploymentReleaseRepository,
            deploymentService,
            installService,
            marketplaceCatalogService,
            vectorizationService,
            auditService
        );

        ShopifyStoreVectorizationSummary summary = service.reconcile("alpha.myshopify.com");

        assertThat(summary.readyToRun()).isTrue();
        assertThat(summary.runnerConfigured()).isTrue();
        assertThat(summary.selectedCategories()).containsExactly("products", "collections", "pages", "policies");
        assertThat(summary.selectedEntityTypes()).containsExactly("product", "support-policy");
        assertThat(summary.requiredPluginIds()).contains(
            ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID,
            ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID
        );
        verify(installService).createInstall(eq("dep-123"), argThat((CreateDeploymentMarketplaceInstallRequest request) ->
            ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID.equals(request.pluginId())
        ));
        verify(installService).createInstall(eq("dep-123"), argThat((CreateDeploymentMarketplaceInstallRequest request) ->
            ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID.equals(request.pluginId())
        ));
        verify(deploymentService, never()).applyVersion(any(), any());
    }

    @Test
    void vectorizeNowQueuesRunForCurrentEnabledEntityScope() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        VectorizationService vectorizationService = mock(VectorizationService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("alpha.myshopify.com");
        store.setDeploymentId("dep-123");
        store.setPagesEnabled(false);
        store.setPoliciesEnabled(false);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("ten-123");

        VectorizationSourceConnectionSummary connection = new VectorizationSourceConnectionSummary(
            "vcn-123", "dep-123", "Shopify store alpha.myshopify.com", "SHOPIFY-STORE", "PRIVATE_RUNTIME_BACKEND_MEDIATED",
            "READY", JSON.objectNode(), JSON.objectNode(), JSON.objectNode(), Instant.now(), Instant.now()
        );
        VectorizationPlanRevisionSummary revision = new VectorizationPlanRevisionSummary(
            "vpr-123", 1, "ACTIVE", "vcn-123", JSON.arrayNode().add("product"), JSON.objectNode(), JSON.objectNode(), "hash-123", Instant.now(), Instant.now()
        );
        VectorizationPlanSummary plan = new VectorizationPlanSummary(
            "vpl-123", "dep-123", "Shopify store vectorization", "ACTIVE", "PLATFORM_MANAGED_AUTO", "IN_SYNC",
            List.of(), JSON.objectNode(), "hash-123", "hash-123", "vpr-123", "vcn-123", null, null, null, null, revision, Instant.now(), Instant.now()
        );
        VectorizationOverviewSummary overview = new VectorizationOverviewSummary(
            "dep-123",
            "cus-123",
            "ten-123",
            "ver-123",
            "v1",
            "cfg-123",
            connection,
            plan,
            new VectorizationRunnerSummary(
                "vrr-123",
                "PLATFORM_MANAGED_AUTO",
                "ACTIVE",
                "COMPATIBLE",
                "hint",
                Instant.now().plusSeconds(3600),
                "vectorization-runner-dep-123",
                "2026.04.track-b",
                "1",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(300)
            ),
            List.of()
        );
        VectorizationOverviewSummary overviewWithRun = new VectorizationOverviewSummary(
            "dep-123", "cus-123", "ten-123", "ver-123", "v1", "cfg-123", connection, plan,
            new VectorizationRunnerSummary(
                "vrr-123",
                "PLATFORM_MANAGED_AUTO",
                "ACTIVE",
                "COMPATIBLE",
                "hint",
                Instant.now().plusSeconds(3600),
                "vectorization-runner-dep-123",
                "2026.04.track-b",
                "1",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(300)
            ),
            List.of(new VectorizationRunSummary(
                "vrn-123",
                "BOOTSTRAP",
                "QUEUED",
                "QUEUED",
                "PLATFORM_MANAGED_AUTO",
                List.of("product"),
                JSON.objectNode(),
                JSON.objectNode(),
                JSON.objectNode(),
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                null,
                null,
                Instant.now()
            ))
        );

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.empty());
        when(installService.listInstalls("dep-123"))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED"),
                install("mpi-catalog", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID, "ENABLED")
            ))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED"),
                install("mpi-catalog", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID, "ENABLED")
            ))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED"),
                install("mpi-catalog", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID, "ENABLED")
            ));
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel(any())).thenReturn("1.0.0");
        when(vectorizationService.upsertSourceConnection(eq("dep-123"), any())).thenReturn(connection);
        when(vectorizationService.upsertPlan(eq("dep-123"), any())).thenReturn(plan);
        when(vectorizationService.getOverviewForTrustedCaller(deployment))
            .thenReturn(overview)
            .thenReturn(overviewWithRun);

        ShopifyStoreVectorizationService service = service(
            repository,
            deploymentRepository,
            deploymentReleaseRepository,
            deploymentService,
            installService,
            marketplaceCatalogService,
            vectorizationService,
            auditService
        );

        ShopifyStoreVectorizationSummary summary = service.vectorizeNow("alpha.myshopify.com");

        assertThat(summary.lastRun()).isNotNull();
        assertThat(summary.lastRun().entityScope()).containsExactly("product");
        verify(vectorizationService).createRun(eq("dep-123"), argThat((CreateVectorizationRunRequest request) ->
            "REFRESH".equals(request.reason()) && request.entityTypes().equals(List.of("product"))
        ));
        verify(deploymentService, never()).applyVersion(any(), any());
    }

    @Test
    void reconcileTriggersApplyWhenPlatformManagedRunnerIsMissing() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        VectorizationService vectorizationService = mock(VectorizationService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("alpha.myshopify.com");
        store.setDeploymentId("dep-123");

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("ten-123");
        deployment.setActiveVersionId("ver-123");

        VectorizationSourceConnectionSummary connection = new VectorizationSourceConnectionSummary(
            "vcn-123",
            "dep-123",
            "Shopify store alpha.myshopify.com",
            "SHOPIFY-STORE",
            "PRIVATE_RUNTIME_BACKEND_MEDIATED",
            "READY",
            JSON.objectNode(),
            JSON.objectNode(),
            JSON.objectNode(),
            Instant.now(),
            Instant.now()
        );
        VectorizationPlanRevisionSummary revision = new VectorizationPlanRevisionSummary(
            "vpr-123",
            1,
            "ACTIVE",
            "vcn-123",
            JSON.arrayNode().add("product").add("support-policy"),
            JSON.objectNode(),
            JSON.objectNode(),
            "hash-123",
            Instant.now(),
            Instant.now()
        );
        VectorizationPlanSummary plan = new VectorizationPlanSummary(
            "vpl-123",
            "dep-123",
            "Shopify store vectorization",
            "ACTIVE",
            "PLATFORM_MANAGED_AUTO",
            "IN_SYNC",
            List.of(),
            JSON.objectNode(),
            "hash-123",
            "hash-123",
            "vpr-123",
            "vcn-123",
            null,
            null,
            null,
            null,
            revision,
            Instant.now(),
            Instant.now()
        );
        VectorizationOverviewSummary overviewWithoutRunner = new VectorizationOverviewSummary(
            "dep-123",
            "cus-123",
            "ten-123",
            "ver-123",
            "v1",
            "cfg-123",
            connection,
            plan,
            null,
            List.of()
        );

        DeploymentReleaseEntity latestRelease = new DeploymentReleaseEntity();
        latestRelease.setId("rel-123");
        latestRelease.setStatus("APPLIED_VERIFIED");
        latestRelease.setProvisioningStatus("ACTIVE");
        latestRelease.setVerificationStatus("PASSED");

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(latestRelease));
        when(installService.listInstalls("dep-123"))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED"),
                install("mpi-catalog", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID, "ENABLED"),
                install("mpi-policies", ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID, "ENABLED")
            ))
            .thenReturn(List.of(
                install("mpi-action", ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID, "ENABLED"),
                install("mpi-embed", ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID, "ENABLED"),
                install("mpi-catalog", ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID, "ENABLED"),
                install("mpi-policies", ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID, "ENABLED")
            ));
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel(any())).thenReturn("1.0.0");
        when(vectorizationService.upsertSourceConnection(eq("dep-123"), any())).thenReturn(connection);
        when(vectorizationService.upsertPlan(eq("dep-123"), any())).thenReturn(plan);
        when(vectorizationService.getOverviewForTrustedCaller(deployment)).thenReturn(overviewWithoutRunner);

        ShopifyStoreVectorizationService service = service(
            repository,
            deploymentRepository,
            deploymentReleaseRepository,
            deploymentService,
            installService,
            marketplaceCatalogService,
            vectorizationService,
            auditService
        );

        ShopifyStoreVectorizationSummary summary = service.reconcile("alpha.myshopify.com");

        assertThat(summary.runnerConfigured()).isFalse();
        assertThat(summary.readyToRun()).isFalse();
        assertThat(summary.blockingReasons()).anyMatch(reason -> reason.contains("vectorization runner"));
        verify(deploymentService).applyVersion("dep-123", "ver-123");
    }

    private ShopifyStoreVectorizationService service(ShopifyStoreConnectionRepository repository,
                                                     DeploymentRepository deploymentRepository,
                                                     DeploymentReleaseRepository deploymentReleaseRepository,
                                                     DeploymentService deploymentService,
                                                     DeploymentMarketplaceInstallService installService,
                                                     MarketplaceCatalogService marketplaceCatalogService,
                                                     VectorizationService vectorizationService,
                                                     PlatformAuditService auditService) {
        return new ShopifyStoreVectorizationService(
            repository,
            deploymentRepository,
            deploymentReleaseRepository,
            deploymentService,
            installService,
            marketplaceCatalogService,
            vectorizationService,
            new ShopifyCompanionBootstrapProperties(
                "dev",
                "dev-openai-qdrant",
                "PLATFORM_MANAGED",
                "SHARED",
                "",
                "",
                "",
                "aws",
                "eu-west-1",
                true,
                "mkp-template-shopify-companion",
                "",
                List.of(
                    ShopifyCompanionPluginSelection.ACTION_READ_PLUGIN_ID,
                    ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID,
                    ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID,
                    ShopifyCompanionPluginSelection.INFERENCE_SHARED_PLUGIN_ID
                )
            ),
            auditService
        );
    }

    private ShopifyStoreConnectionEntity store(String shopDomain) {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain(shopDomain);
        entity.setProductServiceId("psv-123");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private DeploymentMarketplaceInstallSummary install(String id, String pluginId, String status) {
        return new DeploymentMarketplaceInstallSummary(
            id,
            "dep-123",
            pluginId,
            pluginId,
            pluginId,
            "DATA",
            pluginId + "-version",
            "1.0.0",
            status,
            JSON.objectNode(),
            JSON.objectNode(),
            null,
            "READY",
            List.of(),
            null,
            "ACTIVE",
            Instant.now(),
            Instant.now()
        );
    }
}
