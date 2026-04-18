package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceHealthSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourcePreflightSupport;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformManagedProductAdminServiceTest {

    private HttpServer httpServer;

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    @Test
    void listDependentsReturnsStoreMappingsWithResolvedNames() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");
        PlatformCustomerEntity customer = new PlatformCustomerEntity();
        customer.setId("cus-123");
        customer.setName("Demo Customer");
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Shopify Companion");
        deployment.setStatus("ACTIVE");
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("con-123");
        consumer.setConsumerId("demo-storefront");
        consumer.setDisplayName("Demo Storefront");

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        PlatformManagedProductServiceRepository serviceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformManagedProductProvisioningService provisioningService = mock(PlatformManagedProductProvisioningService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findAllByProductServiceIdOrderByShopDomainAsc(service.getId())).thenReturn(List.of(connection));
        when(customerRepository.findById("cus-123")).thenReturn(java.util.Optional.of(customer));
        when(deploymentRepository.findById("dep-123")).thenReturn(java.util.Optional.of(deployment));
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-123")).thenReturn(List.of(version()));
        when(deploymentReleaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(java.util.Optional.of(release()));
        when(consumerRepository.findByConsumerIdIgnoreCase("demo-storefront")).thenReturn(java.util.Optional.of(consumer));

        PlatformManagedProductAdminService adminService = new PlatformManagedProductAdminService(
            serviceService,
            serviceRepository,
            shopifyStoreConnectionRepository,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformSecretService,
            provisioningService,
            platformAuditService,
            railwayGraphqlClient,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ObjectMapper()
        );

        List<ShopifyStoreConnectionSummary> dependents = adminService.listDependents("shopify-bridge-prod");

        assertThat(dependents).hasSize(1);
        assertThat(dependents.get(0).customerName()).isEqualTo("Demo Customer");
        assertThat(dependents.get(0).deploymentName()).isEqualTo("Shopify Companion");
        assertThat(dependents.get(0).consumerDisplayName()).isEqualTo("Demo Storefront");
        assertThat(dependents.get(0).latestVersion()).isNotNull();
        assertThat(dependents.get(0).latestRelease()).isNotNull();
    }

    @Test
    void healthRunsHttpProbeAgainstConfiguredBaseUrl() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/actuator/health", this::handleHealthRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        PlatformManagedProductServiceRepository serviceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformManagedProductProvisioningService provisioningService = mock(PlatformManagedProductProvisioningService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PlatformManagedProductAdminService adminService = new PlatformManagedProductAdminService(
            serviceService,
            serviceRepository,
            shopifyStoreConnectionRepository,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformSecretService,
            provisioningService,
            platformAuditService,
            railwayGraphqlClient,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ObjectMapper()
        );

        PlatformManagedProductServiceHealthSummary health = adminService.getHealth("shopify-bridge-prod");

        assertThat(health.status()).isEqualTo("DEGRADED");
        assertThat(health.healthProbe().status()).isEqualTo("READY");
        assertThat(health.driftStatus()).isEqualTo("RAILWAY_LINKAGE_MISSING");
        verify(serviceRepository).save(service);
    }

    private PlatformManagedProductServiceEntity productService(String serviceRef) {
        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef(serviceRef);
        service.setDisplayName("Shopify Bridge Service");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        service.setDeploymentMode("SHARED_PLATFORM_SERVICE");
        service.setTenantMode("MULTI_TENANT_SHARED");
        service.setDesiredReplicas(1);
        service.setActualReplicas(0);
        service.setMinReplicas(1);
        service.setMaxReplicas(3);
        service.setHealthPath("/actuator/health");
        service.setStatus("CREATED");
        service.setDetailsJson("{}");
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private ShopifyStoreConnectionEntity storeConnection(String serviceId, String shopDomain) {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain(shopDomain);
        entity.setProductServiceId(serviceId);
        entity.setCustomerId("cus-123");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("demo-storefront");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("ENABLED");
        entity.setOnboardingStatus("LIVE");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity entity = new DeploymentVersionEntity();
        entity.setId("ver-123");
        entity.setDeploymentId("dep-123");
        entity.setSourceDraftId("drf-123");
        entity.setVersionLabel("v1");
        entity.setStatus("PUBLISHED");
        entity.setConfigHash("hash-123");
        entity.setPublishedAt(Instant.now());
        return entity;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity entity = new DeploymentReleaseEntity();
        entity.setId("rel-123");
        entity.setDeploymentId("dep-123");
        entity.setDeploymentVersionId("ver-123");
        entity.setStatus("APPLIED_VERIFIED");
        entity.setVerificationStatus("PASSED");
        entity.setProvisioningStatus("SUCCEEDED");
        entity.setProvisioningTarget("RAILWAY");
        entity.setCurrentStepKey("completed");
        entity.setCurrentStepDescription("Release applied and verified.");
        entity.setCreatedAt(Instant.now());
        entity.setAppliedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private void handleHealthRequest(HttpExchange exchange) throws IOException {
        byte[] payload = "{\"status\":\"UP\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
