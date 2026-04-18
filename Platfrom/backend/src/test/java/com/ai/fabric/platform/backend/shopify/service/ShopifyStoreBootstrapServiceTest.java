package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantBindingSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreBootstrapServiceTest {

    @Test
    void bootstrapCreatesCustomerDeploymentConsumerAndBundleWhenMissing() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("demo.myshopify.com");
        PlatformCustomerEntity customer = customerEntity("cus-123");
        PlatformCustomerSummary customerSummary = new PlatformCustomerSummary("cus-123", "Shopify Store demo.myshopify.com", "shopify-store-demo", null, "ACTIVE", false, 0, 0, 0, Instant.now(), Instant.now(), List.of(), List.of());
        DeploymentSummary deployment = deploymentSummary("dep-123", "Shopify Companion demo.myshopify.com", "dev");
        PlatformConsumerSummary consumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", null, "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerTenantService.createCustomer(any())).thenReturn(customerSummary);
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(templateBootstrapService.bootstrap(eq("mkp-template-commerce-shell"), any(CreateMarketplaceTemplateBootstrapRequest.class))).thenReturn(deployment);
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.empty());
        when(customerConsumerService.createConsumer(eq("cus-123"), any())).thenReturn(consumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of());
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-action-shopify-admin")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-commerce-catalog")).thenReturn("1.0.0");
        when(installService.createInstall(eq("dep-123"), any(CreateDeploymentMarketplaceInstallRequest.class))).thenReturn(mock(DeploymentMarketplaceInstallSummary.class));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            connectionService,
            new ShopifyCompanionBootstrapProperties("dev", "custom-start-from-scratch", "", "mkp-template-commerce-shell", "", List.of("mkp-action-shopify-admin", "mkp-data-commerce-catalog")),
            auditService
        );

        ShopifyStoreBootstrapSummary summary = service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        assertThat(summary.createdCustomer()).isTrue();
        assertThat(summary.createdDeployment()).isTrue();
        assertThat(summary.createdConsumer()).isTrue();
        assertThat(summary.customerId()).isEqualTo("cus-123");
        assertThat(summary.deploymentId()).isEqualTo("dep-123");
        assertThat(summary.consumerId()).isEqualTo("shopify-demo");
        assertThat(summary.installedPluginIds()).containsExactly("mkp-template-commerce-shell", "mkp-action-shopify-admin", "mkp-data-commerce-catalog");
        verify(templateBootstrapService).bootstrap(eq("mkp-template-commerce-shell"), any(CreateMarketplaceTemplateBootstrapRequest.class));
        verify(customerConsumerService, never()).updateBinding(eq("cus-123"), eq("shopify-demo"), any());
    }

    @Test
    void bootstrapReusesExistingPlatformObjectsAndOnlyInstallsMissingPlugins() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("demo.myshopify.com");
        store.setCustomerId("cus-123");
        store.setDeploymentId("dep-123");
        store.setConsumerId("shopify-demo");

        PlatformCustomerEntity customer = customerEntity("cus-123");
        DeploymentEntity deploymentEntity = new DeploymentEntity();
        deploymentEntity.setId("dep-123");
        PlatformConsumerEntity consumerEntity = new PlatformConsumerEntity();
        consumerEntity.setId("con-123");
        consumerEntity.setCustomerId("cus-123");
        consumerEntity.setConsumerId("shopify-demo");
        consumerEntity.setDisplayName("Demo Shop");
        consumerEntity.setStatus("ACTIVE");
        consumerEntity.setBoundDeploymentId(null);
        consumerEntity.setCreatedAt(Instant.now());
        consumerEntity.setUpdatedAt(Instant.now());

        DeploymentSummary deployment = deploymentSummary("dep-123", "Shopify Companion demo.myshopify.com", "dev");
        PlatformConsumerSummary reboundConsumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", null, "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deploymentEntity));
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.of(consumerEntity));
        when(customerConsumerService.updateBinding(eq("cus-123"), eq("shopify-demo"), any())).thenReturn(reboundConsumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of(installSummary("mkp-action-shopify-admin")));
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-commerce-catalog")).thenReturn("1.0.0");
        when(installService.createInstall(eq("dep-123"), any(CreateDeploymentMarketplaceInstallRequest.class))).thenReturn(mock(DeploymentMarketplaceInstallSummary.class));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            connectionService,
            new ShopifyCompanionBootstrapProperties("dev", "custom-start-from-scratch", "", "mkp-template-commerce-shell", "", List.of("mkp-action-shopify-admin", "mkp-data-commerce-catalog")),
            auditService
        );

        ShopifyStoreBootstrapSummary summary = service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        assertThat(summary.createdCustomer()).isFalse();
        assertThat(summary.createdDeployment()).isFalse();
        assertThat(summary.createdConsumer()).isFalse();
        assertThat(summary.installedPluginIds()).containsExactly("mkp-action-shopify-admin", "mkp-data-commerce-catalog");
        verify(customerTenantService, never()).createCustomer(any());
        verify(templateBootstrapService, never()).bootstrap(any(), any());
        verify(customerConsumerService).updateBinding(eq("cus-123"), eq("shopify-demo"), any());
    }

    private ShopifyStoreConnectionEntity store(String shopDomain) {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain(shopDomain);
        entity.setDisplayName("Demo Shop");
        entity.setProductServiceId("psv-123");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("NOT_RUN");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("NOT_STARTED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private PlatformCustomerEntity customerEntity(String customerId) {
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId(customerId);
        customer.setName("Shopify Store demo.myshopify.com");
        customer.setSlug("shopify-store-demo");
        customer.setStatus("ACTIVE");
        customer.setCreatedAt(Instant.now());
        customer.setUpdatedAt(Instant.now());
        return customer;
    }

    private DeploymentSummary deploymentSummary(String deploymentId, String name, String environment) {
        return new DeploymentSummary(
            deploymentId,
            name,
            environment,
            "custom-start-from-scratch",
            new DeploymentTenantBindingSummary(
                "cus-123",
                "Shopify Store demo.myshopify.com",
                "shopify-store-demo",
                "ACTIVE",
                false,
                "ten-123",
                "Auto tenant",
                "auto-tenant",
                "ACTIVE",
                false,
                true,
                0,
                0,
                "MUTABLE",
                "Mutable"
            ),
            new DeploymentSourceSummary("github.com/example/repo", "Platform-V5", null, null, false),
            "DRAFT",
            null,
            null,
            false,
            false,
            false,
            Instant.now()
        );
    }

    private DeploymentMarketplaceInstallSummary installSummary(String pluginId) {
        return new DeploymentMarketplaceInstallSummary(
            "mpi-123",
            "dep-123",
            pluginId,
            pluginId,
            pluginId,
            "ACTION",
            "mpv-123",
            "1.0.0",
            "ENABLED",
            null,
            null,
            null,
            "READY",
            List.of(),
            null,
            "DRAFT_ONLY",
            Instant.now(),
            Instant.now()
        );
    }

    private ShopifyStoreConnectionSummary storeSummary(String shopDomain,
                                                       String customerId,
                                                       String deploymentId,
                                                       String consumerId,
                                                       String onboardingStatus) {
        return new ShopifyStoreConnectionSummary(
            "shp-123",
            shopDomain,
            "Demo Shop",
            "psv-123",
            "shopify-bridge-prod",
            "Shopify Bridge Service",
            customerId,
            "Shopify Store demo.myshopify.com",
            deploymentId,
            "Shopify Companion demo.myshopify.com",
            "DRAFT",
            consumerId,
            "Demo Shop",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            onboardingStatus,
            true,
            true,
            true,
            true,
            null,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}
