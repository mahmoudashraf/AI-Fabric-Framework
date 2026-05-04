package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreProvisioningJobEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreProvisioningJobSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreProvisioningJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

class ShopifyStoreProvisioningServiceTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void processExistingStoreUsesTrustedDraftAndVectorizationPaths() {
        ShopifyStoreProvisioningJobRepository jobRepository = mock(ShopifyStoreProvisioningJobRepository.class);
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        ShopifyStoreBootstrapService bootstrapService = mock(ShopifyStoreBootstrapService.class);
        ShopifyStoreVectorizationService vectorizationService = mock(ShopifyStoreVectorizationService.class);
        ShopifyStoreSourcePreflightSupport detailsSupport =
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper().findAndRegisterModules());
        ShopifyCompanionPackageProfileCatalogService profileCatalogService = mock(ShopifyCompanionPackageProfileCatalogService.class);
        DeploymentMarketplaceInstallService marketplaceInstallService = mock(DeploymentMarketplaceInstallService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ShopifyStoreConnectionEntity store = store();
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        ShopifyStoreProvisioningJobEntity job = job();
        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile = profile();

        when(jobRepository.findById("spj-123")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeRepository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(profileCatalogService.resolve("ELITE", "ELITE", "HIGH_QUALITY", "QDRANT_SHARED")).thenReturn(profile);
        when(marketplaceInstallService.listInstallsForTrustedCaller(deployment)).thenReturn(List.of(
            install("mpi-legacy-read", "mkp-action-shopify-companion-read", "ENABLED", "ACTION"),
            install("mpi-inference", profile.inferencePluginId(), "ENABLED", "INFERENCE_PROFILE")
        ));

        ShopifyStoreProvisioningService service = new ShopifyStoreProvisioningService(
            jobRepository,
            storeRepository,
            deploymentRepository,
            bootstrapService,
            vectorizationService,
            detailsSupport,
            profileCatalogService,
            marketplaceInstallService,
            draftCompilerService,
            marketplaceCatalogService,
            auditService,
            new TransactionTemplate(transactionManager)
        );

        ShopifyStoreProvisioningJobSummary summary = service.processJobNow("alpha.myshopify.com", "spj-123");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.phase()).isEqualTo("READY");
        assertThat(store.getDetailsJson()).contains("\"profileKey\":\"HIGH_QUALITY\"");
        assertThat(store.getOnboardingStatus()).isEqualTo("PLATFORM_BOOTSTRAPPED");
        verify(draftCompilerService).syncDeploymentDraftForTrustedCaller("dep-123");
        verify(vectorizationService).reconcileForTrustedCaller("alpha.myshopify.com");
        verify(vectorizationService, never()).reconcile("alpha.myshopify.com");
        verify(bootstrapService, never()).bootstrap(any(), any());
        verify(marketplaceInstallService).updateInstallForTrustedCaller(
            eq(deployment),
            eq("mpi-legacy-read"),
            argThat(request -> request != null && "DISABLED".equals(request.status()))
        );
        verify(marketplaceInstallService).createInstallForTrustedCaller(
            eq(deployment),
            argThat(request -> request != null
                && "mkp-action-shopify-storefront-read-mcp".equals(request.pluginId()))
        );
        verify(marketplaceInstallService).createInstallForTrustedCaller(
            eq(deployment),
            argThat(request -> request != null
                && "mkp-action-shopify-cart-mcp".equals(request.pluginId()))
        );
    }

    @Test
    void processPackageChangeBootstrapsWhenStoredDeploymentIsMissing() {
        ShopifyStoreProvisioningJobRepository jobRepository = mock(ShopifyStoreProvisioningJobRepository.class);
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        ShopifyStoreBootstrapService bootstrapService = mock(ShopifyStoreBootstrapService.class);
        ShopifyStoreVectorizationService vectorizationService = mock(ShopifyStoreVectorizationService.class);
        ShopifyStoreSourcePreflightSupport detailsSupport =
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper().findAndRegisterModules());
        ShopifyCompanionPackageProfileCatalogService profileCatalogService = mock(ShopifyCompanionPackageProfileCatalogService.class);
        DeploymentMarketplaceInstallService marketplaceInstallService = mock(DeploymentMarketplaceInstallService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ShopifyStoreConnectionEntity staleStore = store();
        staleStore.setDeploymentId("dep-missing");
        ShopifyStoreConnectionEntity repairedStore = store();
        repairedStore.setDeploymentId("dep-456");
        DeploymentEntity repairedDeployment = new DeploymentEntity();
        repairedDeployment.setId("dep-456");
        ShopifyStoreProvisioningJobEntity job = job();
        job.setJobType("PACKAGE_CHANGE");
        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile = profile();

        when(jobRepository.findById("spj-123")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeRepository.findByShopDomainIgnoreCase("alpha.myshopify.com"))
            .thenReturn(Optional.of(staleStore), Optional.of(repairedStore));
        when(storeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.findById("dep-missing")).thenReturn(Optional.empty());
        when(deploymentRepository.findById("dep-456")).thenReturn(Optional.of(repairedDeployment));
        when(profileCatalogService.resolve("ELITE", "ELITE", "HIGH_QUALITY", "QDRANT_SHARED")).thenReturn(profile);
        when(bootstrapService.bootstrap(eq("alpha.myshopify.com"), any())).thenReturn(new ShopifyStoreBootstrapSummary(
            "alpha.myshopify.com",
            "cus-123",
            "dep-456",
            "con-123",
            false,
            true,
            false,
            List.of(),
            null
        ));
        when(marketplaceInstallService.listInstallsForTrustedCaller(repairedDeployment)).thenReturn(List.of(
            install("mpi-inference", profile.inferencePluginId(), "ENABLED", "INFERENCE_PROFILE")
        ));

        ShopifyStoreProvisioningService service = new ShopifyStoreProvisioningService(
            jobRepository,
            storeRepository,
            deploymentRepository,
            bootstrapService,
            vectorizationService,
            detailsSupport,
            profileCatalogService,
            marketplaceInstallService,
            draftCompilerService,
            marketplaceCatalogService,
            auditService,
            new TransactionTemplate(transactionManager)
        );

        ShopifyStoreProvisioningJobSummary summary = service.processJobNow("alpha.myshopify.com", "spj-123");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.bootstrapDeploymentId()).isEqualTo("dep-456");
        assertThat(repairedStore.getDetailsJson()).contains("\"profileKey\":\"HIGH_QUALITY\"");
        verify(bootstrapService).bootstrap(eq("alpha.myshopify.com"), any());
        verify(draftCompilerService).syncDeploymentDraftForTrustedCaller("dep-456");
        verify(vectorizationService).reconcileForTrustedCaller("alpha.myshopify.com");
    }

    @Test
    void processNoOpPackageChangePersistsProfileWithoutDeploymentReconciliation() {
        ShopifyStoreProvisioningJobRepository jobRepository = mock(ShopifyStoreProvisioningJobRepository.class);
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        ShopifyStoreBootstrapService bootstrapService = mock(ShopifyStoreBootstrapService.class);
        ShopifyStoreVectorizationService vectorizationService = mock(ShopifyStoreVectorizationService.class);
        ShopifyStoreSourcePreflightSupport detailsSupport =
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper().findAndRegisterModules());
        ShopifyCompanionPackageProfileCatalogService profileCatalogService = mock(ShopifyCompanionPackageProfileCatalogService.class);
        DeploymentMarketplaceInstallService marketplaceInstallService = mock(DeploymentMarketplaceInstallService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        ShopifyStoreConnectionEntity store = store();
        ShopifyStoreProvisioningJobEntity job = job();
        job.setJobType("PACKAGE_CHANGE");
        job.setProfileChangeStrategy("NO_PROFILE_CHANGE");
        job.setVectorReindexRequired(false);
        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile = profile();
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");

        when(jobRepository.findById("spj-123")).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storeRepository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(storeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(profileCatalogService.resolve("ELITE", "ELITE", "HIGH_QUALITY", "QDRANT_SHARED")).thenReturn(profile);

        ShopifyStoreProvisioningService service = new ShopifyStoreProvisioningService(
            jobRepository,
            storeRepository,
            deploymentRepository,
            bootstrapService,
            vectorizationService,
            detailsSupport,
            profileCatalogService,
            marketplaceInstallService,
            draftCompilerService,
            marketplaceCatalogService,
            auditService,
            new TransactionTemplate(transactionManager)
        );

        ShopifyStoreProvisioningJobSummary summary = service.processJobNow("alpha.myshopify.com", "spj-123");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(store.getDetailsJson()).contains("\"profileKey\":\"HIGH_QUALITY\"");
        verify(bootstrapService, never()).bootstrap(any(), any());
        verify(marketplaceInstallService, never()).listInstallsForTrustedCaller(any());
        verify(draftCompilerService, never()).syncDeploymentDraftForTrustedCaller(any());
        verify(vectorizationService, never()).reconcileForTrustedCaller(any());
    }

    private ShopifyStoreConnectionEntity store() {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setCustomerId("cus-123");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("con-123");
        entity.setProductServiceId("psv-123");
        entity.setOnboardingStatus("CONNECTED");
        entity.setDetailsJson("{}");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ShopifyStoreProvisioningJobEntity job() {
        ShopifyStoreProvisioningJobEntity entity = new ShopifyStoreProvisioningJobEntity();
        entity.setId("spj-123");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setStoreConnectionId("shp-123");
        entity.setJobType("MANUAL_REPAIR");
        entity.setStatus("QUEUED");
        entity.setPhase("QUEUED");
        entity.setRequestedPackageKey("ELITE");
        entity.setRequestedTierKey("ELITE");
        entity.setRequestedRuntimeProfileKey("HIGH_QUALITY");
        entity.setRequestedVectorProfileKey("QDRANT_SHARED");
        entity.setRequestedTemplatePluginId("mkp-template-shopify-companion");
        entity.setRequestedPluginIdsJson("[]");
        entity.setProfileChangeStrategy("INITIAL");
        entity.setAttemptCount(0);
        entity.setMaxAttempts(5);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile profile() {
        ShopifyCompanionPackageProfileSummary summary = new ShopifyCompanionPackageProfileSummary(
            "HIGH_QUALITY",
            "ELITE",
            "ELITE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "High quality",
            "Premium generation and shared vector storage.",
            "QUALITY",
            "mkp-template-shopify-companion",
            null,
            "dev-openai-qdrant",
            "mkp-inference-premium-hybrid",
            "qdrant",
            "EXTERNAL_EXISTING",
            "SHARED",
            "starter-launch-readiness",
            "ACTIVE",
            "{}",
            null,
            null
        );
        return new ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile(
            "HIGH_QUALITY",
            "ELITE",
            "ELITE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "High quality",
            "Premium generation and shared vector storage.",
            "QUALITY",
            "mkp-template-shopify-companion",
            null,
            "dev-openai-qdrant",
            "mkp-inference-premium-hybrid",
            "qdrant",
            "EXTERNAL_EXISTING",
            "SHARED",
            "starter-launch-readiness",
            List.of(
                "mkp-action-shopify-storefront-read-mcp",
                "mkp-action-shopify-cart-mcp",
                "mkp-inference-premium-hybrid"
            ),
            List.of(
                "mkp-action-shopify-companion-read",
                "mkp-action-shopify-customer-account-mcp",
                "mkp-action-shopify-checkout-mcp"
            ),
            summary
        );
    }

    private DeploymentMarketplaceInstallSummary install(String id,
                                                        String pluginId,
                                                        String status,
                                                        String pluginType) {
        return new DeploymentMarketplaceInstallSummary(
            id,
            "dep-123",
            pluginId,
            pluginId,
            pluginId,
            pluginType,
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
