package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
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
import static org.mockito.Mockito.when;

class ShopifyStoreConnectionServiceTest {

    @Test
    void upsertConnectionInfersCustomerFromDeploymentWhenMissing() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
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

        when(productServiceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(productServiceService.requireServiceById("psv-123")).thenReturn(service);
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(customerRepository.findById("cus-123")).thenReturn(Optional.of(customer));
        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            consumerRepository,
            platformAuditService
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
    }

    @Test
    void upsertConnectionRejectsMismatchedConsumerDeploymentBinding() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
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
            consumerRepository,
            platformAuditService
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
}
