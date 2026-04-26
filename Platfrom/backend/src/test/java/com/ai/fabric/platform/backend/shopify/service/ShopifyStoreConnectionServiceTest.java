package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetHandleEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetDocumentRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetHandleRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportReadinessSummary;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductStoreSupportReadinessClientService;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreBillingStateRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBindingInspectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreConnectionServiceTest {

    @Test
    void upsertConnectionInfersCustomerFromDeploymentWhenMissing() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        service.setStatus("ACTIVE");
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());

        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("cus-123");
        customer.setName("Demo Customer");

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Shopify Companion");
        deployment.setStatus("ACTIVE");
        deployment.setCustomerId("cus-123");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("draft-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setReindexRequired(false);
        version.setActionsConfigJson("""
            {"actions":[{"name":"list_products"},{"name":"get_policy"}]}
            """);
        version.setKnowledgeSourceConfigJson("""
            {"sources":[{"id":"shopify-catalog"},{"id":"shopify-policies"}]}
            """);
        version.setShellConfigJson("""
            {"modules":[{"id":"search"},{"id":"support"}]}
            """);
        version.setMarketplaceDatasetConfigJson("""
            {"datasets":[{"datasetId":"shopify-products"}]}
            """);
        version.setPublishedAt(Instant.parse("2026-04-18T00:00:00Z"));

        when(productServiceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-123")).thenReturn(java.util.List.of(version));
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        ShopifyStoreConnectionSummary summary = connectionService.upsertConnection(
            new UpsertShopifyStoreConnectionRequest(
                "demo.myshopify.com",
                "Demo Shop",
                "shopify-bridge-prod",
                null,
                "dep-123",
                null,
                "INSTALLED",
                "NOT_SYNCED",
                "NOT_RUN",
                "NOT_ENABLED",
                "NOT_STARTED",
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertThat(summary.customerId()).isEqualTo("cus-123");
        assertThat(summary.deploymentId()).isEqualTo("dep-123");
        assertThat(summary.productServiceRef()).isEqualTo("shopify-bridge-prod");
        assertThat(summary.onboardingStatus()).isEqualTo("NOT_STARTED");
        assertThat(summary.productsEnabled()).isTrue();
        assertThat(summary.collectionsEnabled()).isTrue();
        assertThat(summary.capabilities()).isNotNull();
        assertThat(summary.capabilities().actionNames()).containsExactly("list_products", "get_policy");
        assertThat(summary.capabilities().knowledgeSourceIds()).containsExactly("shopify-catalog", "shopify-policies");
        assertThat(summary.capabilities().shellModuleIds()).containsExactly("search", "support");
        assertThat(summary.capabilities().marketplaceDatasetIds()).containsExactly("shopify-products");
        assertThat(summary.readiness()).isNotNull();
    }

    @Test
    void upsertConnectionRejectsMismatchedConsumerDeploymentBinding() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");

        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("cus-123");

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");

        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("con-123");
        consumer.setConsumerId("demo-storefront");
        consumer.setCustomerId("cus-123");
        consumer.setBoundDeploymentId("dep-other");

        when(productServiceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(consumerRepository.findByConsumerIdIgnoreCase("demo-storefront")).thenReturn(Optional.of(consumer));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        assertThatThrownBy(() -> connectionService.upsertConnection(
            new UpsertShopifyStoreConnectionRequest(
                "demo.myshopify.com",
                null,
                "shopify-bridge-prod",
                "cus-123",
                "dep-123",
                "demo-storefront",
                "INSTALLED",
                "NOT_SYNCED",
                "NOT_RUN",
                "NOT_ENABLED",
                "NOT_STARTED",
                true,
                true,
                true,
                true,
                false,
                false
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("different deployment");
    }

    @Test
    void deleteConnectionRequiresForceWhenBindingsStillExist() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("consumer-123");
        entity.setOnboardingStatus("LIVE");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        assertThatThrownBy(() -> connectionService.deleteConnection("demo.myshopify.com", false))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("active platform bindings");
    }

    @Test
    void recordBillingStatePersistsDurableStoreBillingState() throws Exception {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDetailsJson("{}");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(objectMapper),
            new ShopifyStoreReadinessEvaluator()
        );

        var summary = connectionService.recordBillingState(
            "demo.myshopify.com",
            new RecordShopifyStoreBillingStateRequest(
                "elite",
                "active",
                "sub-1",
                "Loom Companion Elite",
                "live verification"
            )
        );

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.tierKey()).isEqualTo("ELITE");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.subscriptionId()).isEqualTo("sub-1");
        assertThat(summary.recordedAt()).isNotNull();
        assertThat(objectMapper.readTree(entity.getDetailsJson()).path("billingState").path("tierKey").asText())
            .isEqualTo("ELITE");
        verify(repository).save(entity);
        verify(platformAuditService).record(
            org.mockito.ArgumentMatchers.eq("SHOPIFY_STORE_BILLING_STATE_RECORDED"),
            org.mockito.ArgumentMatchers.eq("SHOPIFY_STORE_CONNECTION"),
            org.mockito.ArgumentMatchers.eq("demo.myshopify.com"),
            org.mockito.ArgumentMatchers.anyMap()
        );
    }

    @Test
    void deleteConnectionDeletesWhenForced() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("consumer-123");
        entity.setOnboardingStatus("LIVE");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        connectionService.deleteConnection("demo.myshopify.com", true);

        verify(repository).delete(entity);
    }

    @Test
    void listConnectionsScopesResultsForProductServicePrincipal() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setProductServiceId("psv-123");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("NOT_RUN");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("CONNECTED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(productServiceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(repository.findAllByProductServiceIdOrderByShopDomainAsc("psv-123")).thenReturn(java.util.List.of(entity));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new com.ai.fabric.platform.backend.security.PlatformPrincipal(
                "shopify-bridge-prod",
                com.ai.fabric.platform.backend.security.PlatformRole.PLATFORM_PRODUCT_SERVICE,
                "Shopify Bridge Service",
                "TEST"
            ),
            null,
            java.util.List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_PRODUCT_SERVICE"))
        ));
        try {
            assertThat(connectionService.listConnections())
                .extracting(ShopifyStoreConnectionSummary::shopDomain)
                .containsExactly("alpha.myshopify.com");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void inspectBindingReturnsResolvedCustomerDeploymentAndConsumerDetails() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setProductServiceId("psv-123");
        entity.setCustomerId("cus-123");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("demo-storefront");
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:05:00Z"));

        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("cus-123");
        customer.setName("Demo Customer");
        customer.setSlug("demo-customer");
        customer.setStatus("ACTIVE");
        customer.setPlatformManaged(true);
        customer.setCreatedAt(Instant.parse("2026-04-18T09:00:00Z"));
        customer.setUpdatedAt(Instant.parse("2026-04-18T09:05:00Z"));

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setName("Shopify Companion");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("tpl-shopify");
        deployment.setStatus("ACTIVE");
        deployment.setTenantId("tenant-123");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl("https://runtime.example.com");
        deployment.setConnectorBaseUrl("https://connector.example.com");
        deployment.setApprovalRequiredForApply(false);
        deployment.setApprovalRequiredForDelete(true);
        deployment.setCreatedAt(Instant.parse("2026-04-18T09:10:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-04-18T09:15:00Z"));

        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("con-123");
        consumer.setConsumerId("demo-storefront");
        consumer.setCustomerId("cus-123");
        consumer.setDisplayName("Demo Storefront");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-123");
        consumer.setLastBoundAt(Instant.parse("2026-04-18T09:20:00Z"));
        consumer.setCreatedAt(Instant.parse("2026-04-18T09:18:00Z"));
        consumer.setUpdatedAt(Instant.parse("2026-04-18T09:21:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("drf-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setPublishedAt(Instant.parse("2026-04-18T09:30:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningStatus("SUCCEEDED");
        release.setProvisioningTarget("RAILWAY");
        release.setCurrentStepKey("completed");
        release.setCurrentStepDescription("Release applied and verified.");
        release.setCreatedAt(Instant.parse("2026-04-18T09:35:00Z"));
        release.setAppliedAt(Instant.parse("2026-04-18T09:36:00Z"));
        release.setUpdatedAt(Instant.parse("2026-04-18T09:37:00Z"));

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(consumerRepository.findByConsumerIdIgnoreCase("demo-storefront")).thenReturn(Optional.of(consumer));
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-123")).thenReturn(java.util.List.of(version));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        ShopifyStoreBindingInspectionSummary summary = connectionService.inspectBinding("demo.myshopify.com");

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.productServiceRef()).isEqualTo("shopify-bridge-prod");
        assertThat(summary.customer()).isNotNull();
        assertThat(summary.customer().name()).isEqualTo("Demo Customer");
        assertThat(summary.deployment()).isNotNull();
        assertThat(summary.deployment().name()).isEqualTo("Shopify Companion");
        assertThat(summary.consumer()).isNotNull();
        assertThat(summary.consumer().displayName()).isEqualTo("Demo Storefront");
        assertThat(summary.latestVersion()).isNotNull();
        assertThat(summary.latestRelease()).isNotNull();
        assertThat(summary.warnings()).isEmpty();
    }

    @Test
    void getConnectionReconcilesApplyTimeShopifyDatasetSyncFromVerifiedRelease() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        MarketplaceDatasetHandleRepository marketplaceDatasetHandleRepository = mock(MarketplaceDatasetHandleRepository.class);
        MarketplaceDatasetDocumentRepository marketplaceDatasetDocumentRepository = mock(MarketplaceDatasetDocumentRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setProductServiceId("psv-123");
        entity.setDeploymentId("dep-123");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(false);
        entity.setPagesEnabled(false);
        entity.setPoliciesEnabled(true);
        entity.setDetailsJson("""
            {"credentials":{"status":"READY","accessTokenSecretRef":"secret/access","checkedAt":"2026-04-18T11:59:00Z"}}
            """);
        entity.setCreatedAt(Instant.parse("2026-04-18T11:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T11:05:00Z"));

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Shopify Companion");
        deployment.setStatus("ACTIVE");
        deployment.setTenantId("tenant-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setAppliedAt(Instant.parse("2026-04-18T12:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-04-18T12:01:00Z"));

        MarketplaceDatasetHandleEntity catalogHandle = new MarketplaceDatasetHandleEntity();
        catalogHandle.setId("mdh-cat");
        catalogHandle.setDatasetId("shopify-catalog");
        catalogHandle.setStatus("READY");
        catalogHandle.setLastSyncAt(Instant.parse("2026-04-18T12:02:00Z"));

        MarketplaceDatasetHandleEntity policiesHandle = new MarketplaceDatasetHandleEntity();
        policiesHandle.setId("mdh-pol");
        policiesHandle.setDatasetId("shopify-policies");
        policiesHandle.setStatus("READY");
        policiesHandle.setLastSyncAt(Instant.parse("2026-04-18T12:03:00Z"));

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));
        when(marketplaceDatasetHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123"))
            .thenReturn(java.util.List.of(catalogHandle, policiesHandle));
        when(marketplaceDatasetDocumentRepository.findByDatasetHandleIdOrderByDocumentIdAsc("mdh-cat"))
            .thenReturn(java.util.List.of(new com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetDocumentEntity()));
        when(marketplaceDatasetDocumentRepository.findByDatasetHandleIdOrderByDocumentIdAsc("mdh-pol"))
            .thenReturn(java.util.List.of(
                new com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetDocumentEntity(),
                new com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetDocumentEntity()
            ));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            marketplaceDatasetHandleRepository,
            marketplaceDatasetDocumentRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        ShopifyStoreConnectionSummary summary = connectionService.getConnection("demo.myshopify.com");

        assertThat(summary.syncStatus()).isEqualTo("SYNCED");
        assertThat(summary.syncDetail()).isNotNull();
        assertThat(summary.syncDetail().status()).isEqualTo("SYNCED");
        assertThat(summary.syncDetail().mode()).isEqualTo("APPLY_RELEASE");
        assertThat(summary.syncDetail().documentCount()).isEqualTo(3);
        assertThat(summary.lastSyncAt()).isEqualTo(Instant.parse("2026-04-18T12:03:00Z"));
        verify(repository, times(1)).save(entity);
    }

    @Test
    void getConnectionDoesNotResurrectApplyTimeSyncForUninstalledStore() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        MarketplaceDatasetHandleRepository marketplaceDatasetHandleRepository = mock(MarketplaceDatasetHandleRepository.class);
        MarketplaceDatasetDocumentRepository marketplaceDatasetDocumentRepository = mock(MarketplaceDatasetDocumentRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setProductServiceId("psv-123");
        entity.setDeploymentId("dep-123");
        entity.setInstallStatus("UNINSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("NOT_RUN");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("BLOCKED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(false);
        entity.setPagesEnabled(false);
        entity.setPoliciesEnabled(true);
        entity.setDetailsJson("""
            {
              "credentials":{"status":"MISSING","checkedAt":"2026-04-18T12:04:00Z","expiring":false},
              "sync":{"status":"CLEARED","checkedAt":"2026-04-18T12:05:00Z","mode":"UNINSTALL_CLEANUP","documentCount":2,"message":"Cleared 2 synced Shopify documents."}
            }
            """);
        entity.setCreatedAt(Instant.parse("2026-04-18T11:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T12:05:00Z"));

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Shopify Companion");
        deployment.setStatus("ACTIVE");
        deployment.setTenantId("tenant-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setAppliedAt(Instant.parse("2026-04-18T12:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-04-18T12:01:00Z"));

        MarketplaceDatasetHandleEntity catalogHandle = new MarketplaceDatasetHandleEntity();
        catalogHandle.setId("mdh-cat");
        catalogHandle.setDatasetId("shopify-catalog");
        catalogHandle.setStatus("READY");
        catalogHandle.setLastSyncAt(Instant.parse("2026-04-18T12:02:00Z"));

        MarketplaceDatasetHandleEntity policiesHandle = new MarketplaceDatasetHandleEntity();
        policiesHandle.setId("mdh-pol");
        policiesHandle.setDatasetId("shopify-policies");
        policiesHandle.setStatus("READY");
        policiesHandle.setLastSyncAt(Instant.parse("2026-04-18T12:03:00Z"));

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));
        when(marketplaceDatasetHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc("dep-123"))
            .thenReturn(java.util.List.of(catalogHandle, policiesHandle));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            marketplaceDatasetHandleRepository,
            marketplaceDatasetDocumentRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator()
        );

        ShopifyStoreConnectionSummary summary = connectionService.getConnection("demo.myshopify.com");

        assertThat(summary.installStatus()).isEqualTo("UNINSTALLED");
        assertThat(summary.syncStatus()).isEqualTo("NOT_SYNCED");
        assertThat(summary.syncDetail()).isNotNull();
        assertThat(summary.syncDetail().status()).isEqualTo("CLEARED");
        assertThat(summary.syncDetail().mode()).isEqualTo("UNINSTALL_CLEANUP");
        assertThat(summary.syncDetail().documentCount()).isEqualTo(2);
        verify(repository, times(0)).save(entity);
    }

    @Test
    void getConnectionKeepsFreeTierStoreReadyWhenOrderLookupIsNotEntitled() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformManagedProductStoreSupportReadinessClientService storeSupportReadinessClient =
            mock(PlatformManagedProductStoreSupportReadinessClientService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        service.setStatus("ACTIVE");
        service.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        service.setUpdatedAt(Instant.parse("2026-04-18T10:05:00Z"));

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDisplayName("Demo Shop");
        entity.setProductServiceId("psv-123");
        entity.setCustomerId("cus-123");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("consumer-demo");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("ENABLED");
        entity.setOnboardingStatus("LIVE");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setDetailsJson("""
            {"credentials":{"status":"READY","accessTokenSecretRef":"secret/access","checkedAt":"2026-04-18T11:59:00Z"}}
            """);
        entity.setCreatedAt(Instant.parse("2026-04-18T11:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T11:05:00Z"));

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Shopify Companion");
        deployment.setStatus("ACTIVE");
        deployment.setCustomerId("cus-123");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningStatus("SUCCEEDED");
        release.setProvisioningTarget("RAILWAY");
        release.setCurrentStepKey("completed");
        release.setCurrentStepDescription("Release applied and verified.");
        release.setCreatedAt(Instant.parse("2026-04-18T12:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-04-18T12:01:00Z"));
        release.setUpdatedAt(Instant.parse("2026-04-18T12:02:00Z"));

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));
        when(storeSupportReadinessClient.getStoreSupportReadiness("shopify-bridge-prod", "demo.myshopify.com"))
            .thenReturn(new PlatformManagedProductServiceStoreSupportReadinessSummary(
                "demo.myshopify.com",
                "READY",
                "Order lookup is outside the current Companion tier. Keep order-specific support on merchant handoff unless Elite order lookup is entitled and verified.",
                "SUPPORT_HANDOFF_SETUP",
                false,
                false,
                false,
                false,
                false,
                null,
                false,
                null,
                "INSTALLED",
                "FREE",
                "ACTIVE",
                java.util.List.of("read_products"),
                java.util.List.of(),
                java.util.List.of("Companion Free"),
                java.util.List.of(),
                new PlatformManagedProductServiceStoreSupportProfileSummary(
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
                ),
                false,
                "Merchant support handoff is not configured yet. Add a support email, contact URL, or help center URL before launch.",
                java.util.List.of("No blocking support lifecycle actions remain for this store."),
                java.util.List.of("MERCHANT_SUPPORT_HANDOFF"),
                java.util.List.of("merchant-handoff"),
                java.util.List.of("refunds")
            ));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            storeSupportReadinessClient
        );

        ShopifyStoreConnectionSummary summary = connectionService.getConnection("demo.myshopify.com");

        assertThat(summary.readiness()).isNotNull();
        assertThat(summary.readiness().goLiveEligible()).isTrue();
        assertThat(summary.readiness().storefrontReady()).isTrue();
        assertThat(summary.readiness().goLiveBlockingReasons()).isEmpty();
        assertThat(summary.readiness().storefrontBlockingReasons()).isEmpty();
    }

    @Test
    void getConnectionMarksReadinessBlockedWhenGovernedSupportIsNotReady() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformManagedProductStoreSupportReadinessClientService storeSupportReadinessClient = mock(PlatformManagedProductStoreSupportReadinessClientService.class);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setProductServiceId("psv-123");
        entity.setCustomerId("cus-123");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("consumer-demo");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("ENABLED");
        entity.setOnboardingStatus("LIVE");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setDetailsJson("""
            {"credentials":{"status":"READY","accessTokenSecretRef":"secret/access","checkedAt":"2026-04-18T11:59:00Z"}}
            """);
        entity.setCreatedAt(Instant.parse("2026-04-18T11:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T11:05:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        release.setProvisioningStatus("SUCCEEDED");
        release.setProvisioningTarget("RAILWAY");
        release.setCurrentStepKey("completed");
        release.setCurrentStepDescription("Release applied and verified.");
        release.setCreatedAt(Instant.parse("2026-04-18T12:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-04-18T12:01:00Z"));
        release.setUpdatedAt(Instant.parse("2026-04-18T12:02:00Z"));

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(entity));
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));
        when(storeSupportReadinessClient.getStoreSupportReadiness("shopify-bridge-prod", "demo.myshopify.com"))
            .thenReturn(new PlatformManagedProductServiceStoreSupportReadinessSummary(
                "demo.myshopify.com",
                "PENDING_SCOPE_GRANT",
                "Customer-safe order lookup is waiting for Shopify order-read scope approval on this store.",
                "SCOPE_APPROVAL",
                false,
                false,
                false,
                false,
                false,
                null,
                true,
                "https://shopify-bridge.example.com/auth/shopify/install?shop=demo.myshopify.com",
                "INSTALLED",
                "ELITE",
                "ACTIVE",
                java.util.List.of("read_products"),
                java.util.List.of("read_orders"),
                java.util.List.of(),
                java.util.List.of(),
                new PlatformManagedProductServiceStoreSupportProfileSummary(
                    null,
                    null,
                    null,
                    null,
                    null,
                    false
                ),
                false,
                "Merchant support handoff is not configured yet. Add a support email, contact URL, or help center URL before launch.",
                java.util.List.of(
                    "Grant Shopify read_orders scope so customer-safe order lookup can verify recent orders.",
                    "Configure a merchant support email, contact URL, or help center URL for unsupported order and account cases."
                ),
                java.util.List.of("ORDER_NUMBER_AND_EMAIL"),
                java.util.List.of("order-status"),
                java.util.List.of("refunds")
            ));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            new ShopifyStoreSourcePreflightSupport(new com.fasterxml.jackson.databind.ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            storeSupportReadinessClient
        );

        ShopifyStoreConnectionSummary summary = connectionService.getConnection("demo.myshopify.com");

        assertThat(summary.readiness()).isNotNull();
        assertThat(summary.readiness().goLiveEligible()).isFalse();
        assertThat(summary.readiness().storefrontReady()).isFalse();
        assertThat(summary.readiness().goLiveBlockingReasons())
            .contains("Customer-safe order lookup is waiting for Shopify order-read scope approval on this store.");
        assertThat(summary.readiness().goLiveBlockingReasons())
            .contains("Merchant support handoff is not configured yet. Add a support email, contact URL, or help center URL before launch.");
        assertThat(summary.readiness().nextActions())
            .contains("Grant Shopify read_orders scope so customer-safe order lookup can verify recent orders.");
    }
}
