package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformCustomerSummary;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("demo.myshopify.com");
        PlatformCustomerEntity customer = customerEntity("cus-123");
        PlatformCustomerSummary customerSummary = new PlatformCustomerSummary("cus-123", "Shopify Store demo.myshopify.com", "shopify-store-demo", null, "ACTIVE", false, 0, 0, 0, Instant.now(), Instant.now(), List.of(), List.of());
        DeploymentSummary deployment = deploymentSummary("dep-123", "Shopify Companion demo.myshopify.com", "dev", "dev-openai-qdrant");
        PlatformConsumerSummary consumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", null, "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerTenantService.createCustomer(any())).thenReturn(customerSummary);
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentService.listTemplates()).thenReturn(List.of(templateSummary("dev-openai-qdrant", "qdrant")));
        when(templateBootstrapService.bootstrap(eq("mkp-template-shopify-companion"), any(CreateMarketplaceTemplateBootstrapRequest.class))).thenReturn(deployment);
        when(deploymentService.getActiveDraftForDeployment("dep-123")).thenReturn(draftResponse("dep-123"));
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.empty());
        when(customerConsumerService.createConsumer(eq("cus-123"), any())).thenReturn(consumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of());
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-action-shopify-companion-read")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-shopify-catalog")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-shopify-policies")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-inference-shared-embeddings")).thenReturn("1.0.0");
        when(installService.createInstall(eq("dep-123"), any(CreateDeploymentMarketplaceInstallRequest.class))).thenReturn(mock(DeploymentMarketplaceInstallSummary.class));
        when(productServiceRepository.findById("psv-123")).thenReturn(Optional.of(productService("psv-123")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            managedVectorResourceRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            productServiceRepository,
            connectionService,
            new ShopifyCompanionBootstrapProperties(
                "dev",
                "dev-openai-qdrant",
                "PLATFORM_MANAGED",
                "SHARED",
                "https://shared-qdrant.example",
                "",
                "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED",
                "aws",
                "eu-west-1",
                true,
                "mkp-template-shopify-companion",
                "",
                List.of(
                    "mkp-action-shopify-companion-read",
                    "mkp-data-shopify-catalog",
                    "mkp-data-shopify-policies",
                    "mkp-inference-shared-embeddings"
                )
            ),
            auditService
        );

        ShopifyStoreBootstrapSummary summary = service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        assertThat(summary.createdCustomer()).isTrue();
        assertThat(summary.createdDeployment()).isTrue();
        assertThat(summary.createdConsumer()).isTrue();
        assertThat(summary.customerId()).isEqualTo("cus-123");
        assertThat(summary.deploymentId()).isEqualTo("dep-123");
        assertThat(summary.consumerId()).isEqualTo("shopify-demo");
        assertThat(summary.installedPluginIds()).containsExactly(
            "mkp-template-shopify-companion",
            "mkp-action-shopify-companion-read",
            "mkp-data-shopify-catalog",
            "mkp-data-shopify-policies",
            "mkp-inference-shared-embeddings"
        );
        verify(templateBootstrapService).bootstrap(eq("mkp-template-shopify-companion"), any(CreateMarketplaceTemplateBootstrapRequest.class));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesSharedQdrantDefaults));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesAllowVerifiedSecurity));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesShopifyBridgeRoutingDefaults));
        verify(customerConsumerService, never()).updateBinding(eq("cus-123"), eq("shopify-demo"), any());
    }

    @Test
    void bootstrapReusesExistingPlatformObjectsAndOnlyInstallsMissingPlugins() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
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

        DeploymentSummary deployment = deploymentSummary("dep-123", "Shopify Companion demo.myshopify.com", "dev", "dev-openai-qdrant");
        PlatformConsumerSummary reboundConsumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", null, "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deploymentEntity));
        when(deploymentService.listTemplates()).thenReturn(List.of(templateSummary("dev-openai-qdrant", "qdrant")));
        when(deploymentService.getActiveDraftForDeployment("dep-123")).thenReturn(draftResponse("dep-123"));
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.of(consumerEntity));
        when(customerConsumerService.updateBinding(eq("cus-123"), eq("shopify-demo"), any())).thenReturn(reboundConsumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of(installSummary("mkp-action-shopify-companion-read")));
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-shopify-catalog")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-shopify-policies")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-inference-shared-embeddings")).thenReturn("1.0.0");
        when(installService.createInstall(eq("dep-123"), any(CreateDeploymentMarketplaceInstallRequest.class))).thenReturn(mock(DeploymentMarketplaceInstallSummary.class));
        when(productServiceRepository.findById("psv-123")).thenReturn(Optional.of(productService("psv-123")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            managedVectorResourceRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            productServiceRepository,
            connectionService,
            new ShopifyCompanionBootstrapProperties(
                "dev",
                "dev-openai-qdrant",
                "PLATFORM_MANAGED",
                "SHARED",
                "https://shared-qdrant.example",
                "",
                "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED",
                "aws",
                "eu-west-1",
                true,
                "mkp-template-shopify-companion",
                "",
                List.of(
                    "mkp-action-shopify-companion-read",
                    "mkp-data-shopify-catalog",
                    "mkp-data-shopify-policies",
                    "mkp-inference-shared-embeddings"
                )
            ),
            auditService
        );

        ShopifyStoreBootstrapSummary summary = service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        assertThat(summary.createdCustomer()).isFalse();
        assertThat(summary.createdDeployment()).isFalse();
        assertThat(summary.createdConsumer()).isFalse();
        assertThat(summary.installedPluginIds()).containsExactly(
            "mkp-action-shopify-companion-read",
            "mkp-data-shopify-catalog",
            "mkp-data-shopify-policies",
            "mkp-inference-shared-embeddings"
        );
        verify(customerTenantService, never()).createCustomer(any());
        verify(templateBootstrapService, never()).bootstrap(any(), any());
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesSharedQdrantDefaults));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesAllowVerifiedSecurity));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesShopifyBridgeRoutingDefaults));
        verify(customerConsumerService).updateBinding(eq("cus-123"), eq("shopify-demo"), any());
    }

    @Test
    void bootstrapReactivatesDisabledConsumer() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
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
        consumerEntity.setDescription("Storefront consumer");
        consumerEntity.setStatus("DISABLED");
        consumerEntity.setBoundDeploymentId(null);
        consumerEntity.setCreatedAt(Instant.now());
        consumerEntity.setUpdatedAt(Instant.now());

        PlatformConsumerSummary reboundConsumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", "Storefront consumer", "DISABLED", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        PlatformConsumerSummary activeConsumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", "Storefront consumer", "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deploymentEntity));
        when(deploymentService.listTemplates()).thenReturn(List.of(templateSummary("dev-openai-qdrant", "qdrant")));
        when(deploymentService.getActiveDraftForDeployment("dep-123")).thenReturn(draftResponse("dep-123"));
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.of(consumerEntity));
        when(customerConsumerService.updateBinding(eq("cus-123"), eq("shopify-demo"), any())).thenReturn(reboundConsumer);
        when(customerConsumerService.updateConsumer(eq("cus-123"), eq("shopify-demo"), any(UpdatePlatformConsumerRequest.class))).thenReturn(activeConsumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of());
        when(productServiceRepository.findById("psv-123")).thenReturn(Optional.of(productService("psv-123")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            managedVectorResourceRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            productServiceRepository,
            connectionService,
            new ShopifyCompanionBootstrapProperties("dev", "dev-openai-qdrant", "PLATFORM_MANAGED", "SHARED", "https://shared-qdrant.example", "", "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED", "aws", "eu-west-1", true, "mkp-template-shopify-companion", "", List.of()),
            auditService
        );

        ShopifyStoreBootstrapSummary summary = service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        assertThat(summary.consumerId()).isEqualTo("shopify-demo");
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesSharedQdrantDefaults));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesAllowVerifiedSecurity));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesShopifyBridgeRoutingDefaults));
        verify(customerConsumerService).updateBinding(eq("cus-123"), eq("shopify-demo"), any());
        verify(customerConsumerService).updateConsumer(eq("cus-123"), eq("shopify-demo"), any(UpdatePlatformConsumerRequest.class));
    }

    @Test
    void bootstrapFallsBackToCanonicalQdrantTemplateAndSharedEmbeddingsWhenPropertiesAreStale() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("demo.myshopify.com");
        PlatformCustomerEntity customer = customerEntity("cus-123");
        PlatformCustomerSummary customerSummary = new PlatformCustomerSummary("cus-123", "Shopify Store demo.myshopify.com", "shopify-store-demo", null, "ACTIVE", false, 0, 0, 0, Instant.now(), Instant.now(), List.of(), List.of());
        DeploymentSummary deployment = deploymentSummary("dep-123", "Shopify Companion demo.myshopify.com", "dev", "dev-openai-qdrant");
        PlatformConsumerSummary consumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", null, "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerTenantService.createCustomer(any())).thenReturn(customerSummary);
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentService.listTemplates()).thenReturn(List.of(
            templateSummary("custom-start-from-scratch", "lucene"),
            templateSummary("dev-openai-qdrant", "qdrant")
        ));
        when(templateBootstrapService.bootstrap(eq("mkp-template-shopify-companion"), any(CreateMarketplaceTemplateBootstrapRequest.class))).thenReturn(deployment);
        when(deploymentService.getActiveDraftForDeployment("dep-123")).thenReturn(draftResponse("dep-123"));
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.empty());
        when(customerConsumerService.createConsumer(eq("cus-123"), any())).thenReturn(consumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of());
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-action-shopify-companion-read")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-shopify-catalog")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-data-shopify-policies")).thenReturn("1.0.0");
        when(marketplaceCatalogService.resolveLatestPublishedVersionLabel("mkp-inference-shared-embeddings")).thenReturn("1.0.0");
        when(installService.createInstall(eq("dep-123"), any(CreateDeploymentMarketplaceInstallRequest.class))).thenReturn(mock(DeploymentMarketplaceInstallSummary.class));
        when(productServiceRepository.findById("psv-123")).thenReturn(Optional.of(productService("psv-123")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            managedVectorResourceRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            productServiceRepository,
            connectionService,
            new ShopifyCompanionBootstrapProperties(
                "dev",
                "custom-start-from-scratch",
                "PLATFORM_MANAGED",
                "SHARED",
                "https://shared-qdrant.example",
                "",
                "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED",
                "aws",
                "eu-west-1",
                true,
                "mkp-template-shopify-companion",
                "",
                List.of(
                    "mkp-action-shopify-companion-read",
                    "mkp-data-shopify-catalog",
                    "mkp-data-shopify-policies",
                    "mkp-inference-shopify-companion-default"
                )
            ),
            auditService
        );

        ShopifyStoreBootstrapSummary summary = service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        assertThat(summary.installedPluginIds()).contains("mkp-inference-shared-embeddings");
        verify(templateBootstrapService).bootstrap(eq("mkp-template-shopify-companion"), argThat(request ->
            "dev-openai-qdrant".equals(request.templateId())
                && "PLATFORM_MANAGED".equals(request.vectorProvisioningMode())
        ));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesShopifyBridgeRoutingDefaults));
    }

    @Test
    void bootstrapKeepsPlatformManagedQdrantWhenSharedRootIsNotConfigured() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentManagedVectorResourceRepository managedVectorResourceRepository = mock(DeploymentManagedVectorResourceRepository.class);
        PlatformCustomerTenantService customerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceTemplateBootstrapService templateBootstrapService = mock(MarketplaceTemplateBootstrapService.class);
        DeploymentMarketplaceInstallService installService = mock(DeploymentMarketplaceInstallService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("demo.myshopify.com");
        PlatformCustomerEntity customer = customerEntity("cus-123");
        PlatformCustomerSummary customerSummary = new PlatformCustomerSummary("cus-123", "Shopify Store demo.myshopify.com", "shopify-store-demo", null, "ACTIVE", false, 0, 0, 0, Instant.now(), Instant.now(), List.of(), List.of());
        DeploymentSummary deployment = deploymentSummary("dep-123", "Shopify Companion demo.myshopify.com", "dev", "dev-openai-qdrant");
        PlatformConsumerSummary consumer = new PlatformConsumerSummary("shopify-demo", "cus-123", "Demo Shop", null, "ACTIVE", "dep-123", "Shopify Companion demo.myshopify.com", "dev", "DRAFT", Instant.now(), Instant.now(), Instant.now());
        ShopifyStoreConnectionSummary persisted = storeSummary("demo.myshopify.com", "cus-123", "dep-123", "shopify-demo", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(customerTenantService.createCustomer(any())).thenReturn(customerSummary);
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(deploymentService.listTemplates()).thenReturn(List.of(templateSummary("dev-openai-qdrant", "qdrant")));
        when(templateBootstrapService.bootstrap(eq("mkp-template-shopify-companion"), any(CreateMarketplaceTemplateBootstrapRequest.class))).thenReturn(deployment);
        when(deploymentService.getActiveDraftForDeployment("dep-123")).thenReturn(draftResponseWithProvider("dep-123", provider -> {
            provider.put("vectorProvisioningMode", "EXTERNAL_EXISTING");
            provider.put("vectorStoragePosture", "SHARED");
            provider.put("qdrantHost", "https://shared-qdrant.auto.example");
            provider.put("qdrantRuntimeApiKeySecretName", "MANAGED_QDRANT_DB_API_KEY_DEP_DEP_SHARED");
        }));
        when(consumerRepository.findByConsumerIdIgnoreCase("shopify-demo")).thenReturn(Optional.empty());
        when(customerConsumerService.createConsumer(eq("cus-123"), any())).thenReturn(consumer);
        when(installService.listInstalls("dep-123")).thenReturn(List.of());
        when(productServiceRepository.findById("psv-123")).thenReturn(Optional.of(productService("psv-123")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(persisted);

        ShopifyStoreBootstrapService service = new ShopifyStoreBootstrapService(
            repository,
            customerRepository,
            consumerRepository,
            deploymentRepository,
            managedVectorResourceRepository,
            customerTenantService,
            customerConsumerService,
            deploymentService,
            templateBootstrapService,
            installService,
            marketplaceCatalogService,
            productServiceRepository,
            connectionService,
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
                List.of()
            ),
            auditService
        );

        service.bootstrap("demo.myshopify.com", new BootstrapShopifyStoreRequest(null, null, null, null, null, null, null));

        verify(deploymentService).updateDraft(eq("drf-123"), argThat(request ->
            matchesPlatformManagedQdrantDefaults(request)
                && request.providerConfig().path("qdrantHost").asText("").isBlank()
                && request.providerConfig().path("qdrantRuntimeApiKeySecretName").asText("").isBlank()
        ));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesAllowVerifiedSecurity));
        verify(deploymentService).updateDraft(eq("drf-123"), argThat(this::matchesShopifyBridgeRoutingDefaults));
    }

    private boolean matchesSharedQdrantDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.providerConfig() instanceof ObjectNode provider)) {
            return false;
        }
        return "EXTERNAL_EXISTING".equals(provider.path("vectorProvisioningMode").asText())
            && "SHARED".equals(provider.path("vectorStoragePosture").asText())
            && provider.path("qdrantManagedCollectionsEnabled").asBoolean(false)
            && !provider.path("qdrantHost").asText("").isBlank()
            && !provider.path("qdrantRuntimeApiKeySecretName").asText("").isBlank()
            && provider.path("qdrantCloudProviderId").asText("").isBlank()
            && provider.path("qdrantCloudRegionId").asText("").isBlank();
    }

    private boolean matchesPlatformManagedQdrantDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.providerConfig() instanceof ObjectNode provider)) {
            return false;
        }
        return "PLATFORM_MANAGED".equals(provider.path("vectorProvisioningMode").asText())
            && "SHARED".equals(provider.path("vectorStoragePosture").asText())
            && provider.path("qdrantManagedCollectionsEnabled").asBoolean(false)
            && "aws".equals(provider.path("qdrantCloudProviderId").asText())
            && "eu-west-1".equals(provider.path("qdrantCloudRegionId").asText())
            && provider.path("qdrantHost").asText("").isBlank()
            && provider.path("qdrantRuntimeApiKeySecretName").asText("").isBlank();
    }

    private boolean matchesAllowVerifiedSecurity(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.securityConfig() instanceof ObjectNode security)) {
            return false;
        }
        return "ALLOW_VERIFIED".equals(security.path("authzMode").asText());
    }

    private boolean matchesShopifyBridgeRoutingDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.routingConfig() instanceof ObjectNode routing)) {
            return false;
        }
        ObjectNode upstream = (ObjectNode) routing.path("connector").path("upstream");
        ObjectNode auth = (ObjectNode) upstream.path("auth");
        ObjectNode listProducts = (ObjectNode) routing.path("actions").path("list_products");
        ObjectNode requestBody = (ObjectNode) listProducts.path("request").path("body");
        return "https://shopify-bridge.example.com".equals(upstream.path("base-url").asText())
            && "API_KEY".equals(auth.path("type").asText())
            && "X-BRIDGE-API-KEY".equals(auth.path("header").asText())
            && "${SHOPIFY_BRIDGE_SHARED_SECRET}".equals(auth.path("value").asText())
            && "POST".equals(listProducts.path("method").asText())
            && "/api/admin/stores/demo.myshopify.com/actions/execute".equals(listProducts.path("path").asText())
            && "{{actionId}}".equals(requestBody.path("actionId").asText())
            && "{{params}}".equals(requestBody.path("params").asText())
            && "{{idempotencyKey}}".equals(requestBody.path("idempotencyKey").asText())
            && "{{trace}}".equals(requestBody.path("trace").asText());
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

    private PlatformManagedProductServiceEntity productService(String id) {
        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(id);
        entity.setBaseUrl("https://shopify-bridge.example.com/");
        entity.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        entity.setServiceRef("shopify-bridge-prod");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private DeploymentDraftResponse draftResponse(String deploymentId) {
        ObjectNode provider = JsonNodeFactory.instance.objectNode();
        provider.put("llmProvider", "openai");
        provider.put("embeddingProvider", "openai");
        provider.put("vectorStrategy", "qdrant");
        return draftResponseWithProvider(deploymentId, provider);
    }

    private DeploymentDraftResponse draftResponseWithProvider(String deploymentId, Consumer<ObjectNode> customizer) {
        ObjectNode provider = JsonNodeFactory.instance.objectNode();
        provider.put("llmProvider", "openai");
        provider.put("embeddingProvider", "openai");
        provider.put("vectorStrategy", "qdrant");
        customizer.accept(provider);
        return draftResponseWithProvider(deploymentId, provider);
    }

    private DeploymentDraftResponse draftResponseWithProvider(String deploymentId, ObjectNode provider) {
        return new DeploymentDraftResponse(
            "drf-123",
            deploymentId,
            1,
            "ACTIVE",
            JsonNodeFactory.instance.objectNode(),
            JsonNodeFactory.instance.objectNode(),
            JsonNodeFactory.instance.objectNode(),
            provider,
            JsonNodeFactory.instance.objectNode(),
            JsonNodeFactory.instance.objectNode(),
            Instant.now(),
            Instant.now()
        );
    }

    private DeploymentTemplateSummary templateSummary(String id, String vectorStrategy) {
        return new DeploymentTemplateSummary(
            id,
            id,
            id + " description",
            "openai",
            "openai",
            vectorStrategy,
            "runtime-managed",
            "connector-hosted",
            "qdrant".equals(vectorStrategy),
            "qdrant".equals(vectorStrategy) ? "PLATFORM_MANAGED" : "",
            vectorStrategy + " summary"
        );
    }

    private DeploymentSummary deploymentSummary(String deploymentId, String name, String environment, String templateId) {
        return new DeploymentSummary(
            deploymentId,
            name,
            environment,
            templateId,
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
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}
