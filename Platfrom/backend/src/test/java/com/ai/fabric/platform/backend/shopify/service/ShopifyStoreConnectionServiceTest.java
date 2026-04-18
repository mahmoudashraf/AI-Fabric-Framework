package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
                true
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
}
