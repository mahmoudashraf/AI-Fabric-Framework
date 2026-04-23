package com.ai.fabric.platform.backend.productservice.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayLogTagsSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.RailwayGraphqlClient;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceDeploymentHistorySummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceHealthSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceRailwayLogsSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBindingInspectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreConnectionService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreReadinessEvaluator;
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
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        List<ShopifyStoreConnectionSummary> dependents = adminService.listDependents("shopify-bridge-prod");

        assertThat(dependents).hasSize(1);
        assertThat(dependents.get(0).customerName()).isEqualTo("Demo Customer");
        assertThat(dependents.get(0).deploymentName()).isEqualTo("Shopify Companion");
        assertThat(dependents.get(0).consumerDisplayName()).isEqualTo("Demo Storefront");
        assertThat(dependents.get(0).latestVersion()).isNotNull();
        assertThat(dependents.get(0).latestRelease()).isNotNull();
        assertThat(dependents.get(0).readiness()).isNotNull();
        assertThat(dependents.get(0).readiness().overallStatus()).isEqualTo("STOREFRONT_READY");
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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        PlatformManagedProductServiceHealthSummary health = adminService.getHealth("shopify-bridge-prod");

        assertThat(health.status()).isEqualTo("DEGRADED");
        assertThat(health.healthProbe().status()).isEqualTo("READY");
        assertThat(health.driftStatus()).isEqualTo("RAILWAY_LINKAGE_MISSING");
        verify(serviceRepository).save(service);
    }

    @Test
    void overviewFetchesBridgeAdminOverviewUsingManagedSecret() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/overview", this::handleOverviewRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");
        when(platformSecretService.isSecretPresent("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn(true);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        var overview = adminService.getOverview("shopify-bridge-prod");

        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.installs().totalCount()).isEqualTo(5);
        assertThat(overview.stores().storefrontReadyCount()).isEqualTo(1);
        assertThat(overview.webhookSubscriptions()).isNotNull();
        assertThat(overview.webhookSubscriptions().expectedCount()).isEqualTo(11);
        assertThat(overview.billing()).isNotNull();
        assertThat(overview.billing().mode()).isEqualTo("FREE");
        assertThat(overview.usage()).isNotNull();
        assertThat(overview.usage().activeShopsLast7Days()).isEqualTo(2);
        assertThat(overview.usage().totalToday()).isEqualTo(4);
        assertThat(overview.summaryMessage()).contains("Platform store mappings resolved successfully");
    }

    @Test
    void getServiceSummaryRepairsRailwayBindingBeforeReturningSummary() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setRailwayProjectId("prj-456");
        service.setRailwayEnvironmentId("env-456");
        service.setRailwayServiceId("svc-456");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        PlatformManagedProductServiceSummary expectedSummary = new PlatformManagedProductServiceSummary(
            service.getId(),
            service.getServiceRef(),
            service.getDisplayName(),
            service.getProductFamily(),
            service.getServiceKind(),
            service.getDeploymentMode(),
            service.getTenantMode(),
            service.getEnvironmentScope(),
            service.getDeploymentId(),
            "prj-456",
            "env-456",
            "svc-456",
            service.getDesiredReplicas(),
            service.getActualReplicas(),
            service.getMinReplicas(),
            service.getMaxReplicas(),
            service.getBaseUrl(),
            service.getPrivateNetworkUrl(),
            service.getHealthPath(),
            service.getServiceRoot(),
            service.getDockerfilePath(),
            service.getSecretName(),
            service.getStatus(),
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
            null,
            null,
            null,
            null,
            0,
            0
        );

        when(provisioningService.refreshRailwayBindingFromWorkspace("shopify-bridge-prod")).thenReturn(service);
        when(serviceService.toSummary(service)).thenReturn(expectedSummary);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        PlatformManagedProductServiceSummary summary = adminService.getServiceSummary("shopify-bridge-prod");

        assertThat(summary.railwayProjectId()).isEqualTo("prj-456");
        assertThat(summary.railwayEnvironmentId()).isEqualTo("env-456");
        assertThat(summary.railwayServiceId()).isEqualTo("svc-456");
        verify(provisioningService).refreshRailwayBindingFromWorkspace("shopify-bridge-prod");
    }

    @Test
    void getStoreBindingReturnsShopifyBindingInspectionForMappedStore() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");
        ShopifyStoreBindingInspectionSummary inspection = new ShopifyStoreBindingInspectionSummary(
            "demo.myshopify.com",
            "shopify-bridge-prod",
            null,
            null,
            null,
            versionSummary(),
            releaseSummary(),
            List.of("warning")
        );

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(shopifyStoreConnectionService.inspectBinding("demo.myshopify.com")).thenReturn(inspection);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        ShopifyStoreBindingInspectionSummary summary = adminService.getStoreBinding("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.productServiceRef()).isEqualTo("shopify-bridge-prod");
        assertThat(summary.latestVersion()).isNotNull();
        assertThat(summary.latestRelease()).isNotNull();
        assertThat(summary.warnings()).containsExactly("warning");
    }

    @Test
    void runStoreSourcePreflightCallsBridgeAdminEndpointAndReturnsPlatformSummary() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/run-source-preflight", this::handleRunSourcePreflightRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");
        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");
        ShopifyStoreConnectionSummary storeSummary = summary(connection);

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");
        when(shopifyStoreConnectionService.getConnection("demo.myshopify.com")).thenReturn(storeSummary);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        ShopifyStoreConnectionSummary result = adminService.runStoreSourcePreflight("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(result.shopDomain()).isEqualTo("demo.myshopify.com");
        verify(platformAuditService).record(
            org.mockito.ArgumentMatchers.eq("MANAGED_PRODUCT_SOURCE_PREFLIGHT_TRIGGERED"),
            org.mockito.ArgumentMatchers.eq("MANAGED_PRODUCT_SERVICE"),
            org.mockito.ArgumentMatchers.eq("shopify-bridge-prod"),
            any()
        );
    }

    @Test
    void runStoreSourcePreflightSurfacesBridgeConflictMessage() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/run-source-preflight", this::handleRunSourcePreflightConflictRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");
        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        assertThatThrownBy(() -> adminService.runStoreSourcePreflight("shopify-bridge-prod", "demo.myshopify.com"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("persisted store credentials");
    }

    @Test
    void deploymentHistoryReturnsRecentRailwayDeployments() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setRailwayProjectId("rail-project-123");
        service.setRailwayEnvironmentId("rail-env-123");
        service.setRailwayServiceId("rail-svc-123");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(provisioningService.refreshRailwayBindingFromWorkspace("shopify-bridge-prod")).thenReturn(service);
        when(railwayGraphqlClient.listServiceDeployments("rail-svc-123", 5)).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayDeploymentSummary(
                "rail-dep-2",
                "SUCCESS",
                "https://deploy-2.example.com",
                "https://static-2.example.com",
                "2026-04-18T17:00:00Z"
            ),
            new RailwayGraphqlClient.RailwayDeploymentSummary(
                "rail-dep-1",
                "REMOVED",
                "https://deploy-1.example.com",
                null,
                "2026-04-18T16:00:00Z"
            )
        ));

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        PlatformManagedProductServiceDeploymentHistorySummary summary = adminService.getDeploymentHistory("shopify-bridge-prod", 5);

        assertThat(summary.available()).isTrue();
        assertThat(summary.railwayServiceId()).isEqualTo("rail-svc-123");
        assertThat(summary.deployments()).hasSize(2);
        assertThat(summary.deployments().get(0).id()).isEqualTo("rail-dep-2");
        assertThat(summary.deployments().get(0).status()).isEqualTo("SUCCESS");
    }

    @Test
    void railwayLogsResolveLatestDeploymentWhenDeploymentIdIsMissing() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setRailwayProjectId("rail-project-123");
        service.setRailwayEnvironmentId("rail-env-123");
        service.setRailwayServiceId("rail-svc-123");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(provisioningService.refreshRailwayBindingFromWorkspace("shopify-bridge-prod")).thenReturn(service);
        when(railwayGraphqlClient.listServiceDeployments("rail-svc-123", 1)).thenReturn(List.of(
            new RailwayGraphqlClient.RailwayDeploymentSummary(
                "rail-dep-9",
                "SUCCESS",
                "https://deploy-9.example.com",
                "https://static-9.example.com",
                "2026-04-18T17:00:00Z"
            )
        ));
        when(railwayGraphqlClient.fetchDeploymentLogs("rail-dep-9", 50, null, null, null)).thenReturn(List.of(
            new RailwayLogEntrySummary(
                "2026-04-18T17:01:00Z",
                "INFO",
                "Bridge started successfully.",
                new RailwayLogTagsSummary("rail-dep-9", "inst-1", "rail-env-123", "rail-project-123", "rail-svc-123", "snap-1"),
                List.of()
            )
        ));

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        PlatformManagedProductServiceRailwayLogsSummary summary = adminService.getRailwayLogs(
            "shopify-bridge-prod",
            "deployment",
            null,
            50,
            null,
            null,
            null
        );

        assertThat(summary.available()).isTrue();
        assertThat(summary.railwayDeploymentId()).isEqualTo("rail-dep-9");
        assertThat(summary.entries()).hasSize(1);
        assertThat(summary.entries().get(0).message()).contains("Bridge started successfully");
    }

    @Test
    void storeWebhookSubscriptionsAreFetchedThroughBridgeAdminApi() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/webhook-subscriptions", this::handleWebhookSubscriptionsRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");

        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        var summary = adminService.getStoreWebhookSubscriptions("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.status()).isEqualTo("DEGRADED");
        assertThat(summary.missingCount()).isEqualTo(1);
        assertThat(summary.topics()).hasSize(1);
    }

    @Test
    void storeBillingSummaryIsFetchedThroughBridgeAdminApi() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/billing-summary", this::handleBillingSummaryRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");

        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        var summary = adminService.getStoreBillingSummary("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.mode()).isEqualTo("PAID");
        assertThat(summary.status()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(summary.merchantApprovalRequired()).isTrue();
        assertThat(summary.launchBlocked()).isTrue();
        assertThat(summary.availablePlans()).hasSize(2);
        assertThat(summary.requiresExplicitConfirmation()).isTrue();
        assertThat(summary.auditTrailAvailable()).isTrue();
        assertThat(summary.actionPackages()).containsExactly("guided-commerce");
        assertThat(summary.availablePlans().get(0).tierKey()).isEqualTo("FREE");
        assertThat(summary.availablePlans().get(0).chatFallbackEnabled()).isFalse();
        assertThat(summary.availablePlans().get(1).tierKey()).isEqualTo("STARTER");
        assertThat(summary.availablePlans().get(1).allowedSurfaces()).contains("comparison");
        assertThat(summary.availablePlans().get(1).requiresExplicitConfirmation()).isTrue();
        assertThat(summary.availablePlans().get(1).auditTrailAvailable()).isTrue();
        assertThat(summary.availablePlans().get(1).actionPackages()).containsExactly("guided-commerce");
    }

    @Test
    void storeUsageSummaryIsFetchedThroughBridgeAdminApi() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/usage-summary", this::handleUsageSummaryRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");

        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        var summary = adminService.getStoreUsageSummary("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.totalToday()).isEqualTo(4);
        assertThat(summary.todaySurfaceUsage()).hasSize(1);
        assertThat(summary.todaySurfaceUsage().get(0).surfaceId()).isEqualTo("ai-search");
        assertThat(summary.topQuestionsLast7Days()).hasSize(1);
        assertThat(summary.topQuestionsLast7Days().get(0).queryText()).isEqualTo("where is my order");
        assertThat(summary.roiSummary()).isNotNull();
        assertThat(summary.roiSummary().status()).isEqualTo("READY");
        assertThat(summary.roiSummary().strongestSurfaceLabels()).containsExactly("AI Search", "Order Lookup");
    }

    @Test
    void storeVectorizationSummaryIsFetchedThroughBridgeAdminApi() throws Exception {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.createContext("/api/admin/stores/demo.myshopify.com/vectorization", this::handleVectorizationSummaryRequest);
        httpServer.start();
        String baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl(baseUrl);
        service.setSecretName("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY");

        ShopifyStoreConnectionEntity connection = storeConnection(service.getId(), "demo.myshopify.com");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.findByProductServiceIdAndShopDomainIgnoreCase(service.getId(), "demo.myshopify.com"))
            .thenReturn(java.util.Optional.of(connection));
        when(platformSecretService.resolveSecret("MANAGED_SHOPIFY_BRIDGE_ADMIN_KEY")).thenReturn("bridge-admin-key");

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        var summary = adminService.getStoreVectorizationSummary("shopify-bridge-prod", "demo.myshopify.com");

        assertThat(summary.shopDomain()).isEqualTo("demo.myshopify.com");
        assertThat(summary.readyToRun()).isTrue();
        assertThat(summary.selectedCategories()).containsExactly("products", "metaobjects");
        assertThat(summary.selectedEntityTypes()).containsExactly("product", "support-policy");
        assertThat(summary.policy()).isNotNull();
        assertThat(summary.policy().policyVersion()).isEqualTo(3L);
        assertThat(summary.effectiveIndexedFields()).hasSize(1);
        assertThat(summary.effectiveIndexedFields().get(0).fieldKey()).isEqualTo("products.title");
        assertThat(summary.automation()).isNotNull();
        assertThat(summary.automation().autoIndexingHealthy()).isTrue();
        assertThat(summary.recentEvents()).hasSize(1);
        assertThat(summary.recentEvents().get(0).sourceCategory()).isEqualTo("products");
    }

    @Test
    void decommissionRejectsServicesWithDependentStores() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");

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
        ShopifyStoreConnectionService shopifyStoreConnectionService = mock(ShopifyStoreConnectionService.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.countByProductServiceId(service.getId())).thenReturn(2L);

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
            shopifyStoreConnectionService,
            new ShopifyStoreSourcePreflightSupport(new ObjectMapper()),
            new ShopifyStoreReadinessEvaluator(),
            new ObjectMapper()
        );

        assertThatThrownBy(() -> adminService.decommission("shopify-bridge-prod"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("dependent Shopify store mapping");

        verify(provisioningService, never()).decommission("shopify-bridge-prod");
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
        entity.setDetailsJson("""
            {
              "credentials": {
                "status": "READY",
                "checkedAt": "2026-04-18T10:00:00Z",
                "accessTokenSecretRef": "MANAGED_SHOPIFY_ACCESS_TOKEN_DEMO_AAAAAA",
                "refreshTokenSecretRef": "MANAGED_SHOPIFY_REFRESH_TOKEN_DEMO_BBBBBB",
                "accessTokenExpiresAt": "2026-04-18T11:00:00Z",
                "refreshTokenExpiresAt": "2026-07-18T10:00:00Z",
                "scopesText": "read_products,read_content",
                "expiring": false
              },
              "widget": {
                "status": "ENABLED",
                "checkedAt": "2026-04-18T10:05:00Z",
                "channel": "THEME_APP_EXTENSION",
                "message": "Theme app extension enabled."
              },
              "sync": {
                "status": "SYNCED",
                "checkedAt": "2026-04-18T10:04:00Z",
                "mode": "FULL",
                "documentCount": 128,
                "message": "Initial import completed."
              }
            }
            """);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ShopifyStoreConnectionSummary summary(ShopifyStoreConnectionEntity entity) {
        return new ShopifyStoreConnectionSummary(
            entity.getId(),
            entity.getShopDomain(),
            entity.getShopDomain(),
            entity.getProductServiceId(),
            "shopify-bridge-prod",
            "Shopify Bridge Service",
            entity.getCustomerId(),
            "Demo Customer",
            entity.getDeploymentId(),
            "Demo Deployment",
            "ACTIVE",
            entity.getConsumerId(),
            "Demo Storefront",
            entity.getInstallStatus(),
            entity.getSyncStatus(),
            entity.getSourceReadinessStatus(),
            entity.getWidgetStatus(),
            entity.getOnboardingStatus(),
            entity.isProductsEnabled(),
            entity.isCollectionsEnabled(),
            entity.isPagesEnabled(),
            entity.isPoliciesEnabled(),
            entity.isArticlesEnabled(),
            entity.isMetaobjectsEnabled(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            versionSummary(),
            releaseSummary(),
            null,
            null,
            null,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
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

    private DeploymentVersionSummary versionSummary() {
        return new DeploymentVersionSummary(
            "ver-123",
            "dep-123",
            "drf-123",
            "v1",
            "PUBLISHED",
            "hash-123",
            false,
            Instant.now()
        );
    }

    private DeploymentReleaseSummary releaseSummary() {
        return new DeploymentReleaseSummary(
            "rel-123",
            "dep-123",
            "ver-123",
            "APPLIED_VERIFIED",
            "PASSED",
            "SUCCEEDED",
            "RAILWAY",
            "completed",
            "Release applied and verified.",
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
    }

    private void handleHealthRequest(HttpExchange exchange) throws IOException {
        byte[] payload = "{\"status\":\"UP\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void handleOverviewRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        byte[] payload = """
            {
              "appName": "Shopify Bridge Service",
              "serviceRef": "shopify-bridge-prod",
              "productFamily": "SHOPIFY",
              "serviceKind": "SHOPIFY_BRIDGE_SERVICE",
              "environmentScope": "prod",
              "platformBaseUrl": "https://platform.example.com",
              "publicBaseUrl": "https://bridge.example.com",
              "adminApiKeyConfigured": true,
              "status": "READY",
              "serverStartedAt": "2026-04-18T10:00:00Z",
              "installs": {
                "totalCount": 5,
                "installedCount": 4,
                "uninstalledCount": 1,
                "credentialReadyCount": 4,
                "lastAuthenticatedAt": "2026-04-18T10:10:00Z",
                "lastUninstalledAt": "2026-04-18T09:00:00Z"
              },
              "stores": {
                "platformAccessStatus": "READY",
                "platformAccessMessage": "Platform store mappings resolved successfully.",
                "totalCount": 3,
                "readyForGoLiveCount": 2,
                "storefrontReadyCount": 1,
                "liveCount": 1,
                "blockedCount": 1,
                "lastWebhookAt": "2026-04-18T10:15:00Z"
              },
              "webhookSubscriptions": {
                "status": "READY",
                "message": "Diagnostics available.",
                "webhookUri": "https://bridge.example.com/api/webhooks/shopify",
                "expectedCount": 11,
                "expectedTopics": ["APP_UNINSTALLED", "PRODUCTS_CREATE"]
              },
              "billing": {
                "mode": "FREE",
                "planName": "Companion Free",
                "status": "ACTIVE",
                "merchantApprovalRequired": false,
                "launchBlocked": false,
                "message": "Free mode."
              },
              "usage": {
                "generatedAt": "2026-04-18T10:20:00Z",
                "lastActivityAt": "2026-04-18T10:18:00Z",
                "activeShopsToday": 1,
                "activeShopsLast7Days": 2,
                "totalToday": 4,
                "totalLast7Days": 9,
                "todayBreakdown": [
                  {"eventType": "MERCHANT_GO_LIVE", "count": 1},
                  {"eventType": "STOREFRONT_WIDGET_OPENED_HOME_PAGE", "count": 3}
                ],
                "last7DayBreakdown": [
                  {"eventType": "MERCHANT_SYNC_NOW", "count": 2},
                  {"eventType": "STOREFRONT_WIDGET_OPENED_HOME_PAGE", "count": 7}
                ]
              },
              "capabilities": ["managed-service-health", "billing-posture-summary"],
              "notYetImplemented": []
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void handleWebhookSubscriptionsRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        byte[] payload = """
            {
              "shopDomain": "demo.myshopify.com",
              "status": "DEGRADED",
              "message": "One topic is missing.",
              "webhookUri": "https://bridge.example.com/api/webhooks/shopify",
              "expectedCount": 11,
              "readyCount": 10,
              "missingCount": 1,
              "driftedCount": 0,
              "checkedAt": "2026-04-18T10:25:00Z",
              "topics": [
                {
                  "topic": "PRODUCTS_UPDATE",
                  "expectedName": "loom-products-update",
                  "status": "MISSING",
                  "subscriptionId": null,
                  "subscriptionName": null,
                  "subscriptionUri": null,
                  "message": "Required Shopify webhook subscription is missing."
                }
              ]
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
          outputStream.write(payload);
        }
    }

    private void handleBillingSummaryRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        byte[] payload = """
            {
              "mode": "PAID",
              "tierKey": "FREE",
              "planName": "Companion Growth",
              "status": "READY_FOR_APPROVAL",
              "merchantApprovalRequired": true,
              "launchBlocked": true,
              "requiresExplicitConfirmation": true,
              "auditTrailAvailable": true,
              "actionPackages": ["guided-commerce"],
              "chatFallbackEnabled": false,
              "allowedSurfaces": ["ai-search"],
              "availablePlans": [
                {
                  "tierKey": "FREE",
                  "planName": "Loom Companion Free",
                  "active": true,
                  "commerciallyAvailable": true,
                  "merchantApprovalSupported": false,
                  "actionCapable": false,
                  "catalogProductCap": 50,
                  "syncCadence": "DAILY",
                  "poweredByBadgeRequired": true,
                  "chatFallbackEnabled": false,
                  "allowedSurfaces": ["ai-search"],
                  "message": "Free tier is active."
                },
                {
                  "tierKey": "STARTER",
                  "planName": "Loom Companion Starter",
                  "amount": "29.00",
                  "currencyCode": "USD",
                  "interval": "EVERY_30_DAYS",
                  "active": false,
                  "commerciallyAvailable": true,
                  "merchantApprovalSupported": true,
                  "actionCapable": false,
                  "catalogProductCap": null,
                  "syncCadence": "EVERY_2_HOURS",
                  "poweredByBadgeRequired": false,
                  "chatFallbackEnabled": true,
                  "requiresExplicitConfirmation": true,
                  "auditTrailAvailable": true,
                  "actionPackages": ["guided-commerce"],
                  "allowedSurfaces": ["ai-search", "comparison"],
                  "message": "Starter expands the embedded intelligence surface set."
                }
              ],
              "message": "Merchant approval is required before go-live."
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void handleUsageSummaryRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        byte[] payload = """
            {
              "shopDomain": "demo.myshopify.com",
              "generatedAt": "2026-04-18T10:20:00Z",
              "lastActivityAt": "2026-04-18T10:18:00Z",
              "totalToday": 4,
              "totalLast7Days": 9,
              "todayBreakdown": [
                {"eventType": "STOREFRONT_SHOPPER_QUERY", "count": 3}
              ],
              "last7DayBreakdown": [
                {"eventType": "STOREFRONT_ORDER_LOOKUP", "count": 5}
              ],
              "todaySurfaceUsage": [
                {"surfaceId": "ai-search", "label": "AI Search", "count": 3}
              ],
              "last7DaySurfaceUsage": [
                {"surfaceId": "order-lookup", "label": "Order Lookup", "count": 5}
              ],
              "topQuestionsLast7Days": [
                {
                  "surfaceId": "ai-search",
                  "label": "AI Search",
                  "queryText": "where is my order",
                  "count": 2,
                  "lastAskedAt": "2026-04-18T10:18:00Z"
                }
              ],
              "last7DaySurfaceJourneys": [
                {
                  "surfaceId": "order-lookup",
                  "label": "Order Lookup",
                  "shopperQuestions": 3,
                  "shopperInteractions": 3,
                  "readActions": 2,
                  "governedActionGrants": 0,
                  "governedActionCompletions": 0,
                  "governedActionFailures": 0
                }
              ],
              "roiSummary": {
                "status": "READY",
                "message": "Healthy bounded ROI signals are available.",
                "shopperAssistSignals": 7,
                "decisionSupportSignals": 2,
                "governedActionGrants": 1,
                "governedActionCompletions": 1,
                "governedActionFailures": 0,
                "activeSurfaceCount": 2,
                "strongestSurfaceLabels": ["AI Search", "Order Lookup"],
                "recommendations": ["Keep indexing products"]
              }
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void handleVectorizationSummaryRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        byte[] payload = """
            {
              "shopDomain": "demo.myshopify.com",
              "deploymentId": "dep-123",
              "bootstrapped": true,
              "selectedCategories": ["products", "metaobjects"],
              "selectedEntityTypes": ["product", "support-policy"],
              "requiredPluginIds": ["plugin-a"],
              "installedPluginIds": ["plugin-a"],
              "missingPluginIds": [],
              "disabledPluginIds": [],
              "reconciliationRequired": false,
              "connectionConfigured": true,
              "sourceConnectionId": "src-123",
              "sourceConnectionStatus": "READY",
              "sourceAdapterType": "SHOPIFY",
              "planConfigured": true,
              "planId": "plan-123",
              "planStatus": "ACTIVE",
              "runnerConfigured": true,
              "runnerRegistrationId": "runner-123",
              "runnerRegistrationStatus": "REGISTERED",
              "deploymentApplyInProgress": false,
              "deploymentApplyStatus": "SUCCEEDED",
              "runnerMode": "AUTO",
              "syncState": "SYNCED",
              "readyToRun": true,
              "blockingReasons": [],
              "lastRun": {
                "id": "run-1",
                "reason": "MANUAL",
                "status": "SUCCEEDED",
                "requestedStatus": "SUCCEEDED",
                "entityScope": ["product"],
                "createdAt": "2026-04-18T10:00:00Z",
                "startedAt": "2026-04-18T10:01:00Z",
                "completedAt": "2026-04-18T10:03:00Z",
                "updatedAt": "2026-04-18T10:03:00Z"
              },
              "policy": {
                "policyVersion": 3,
                "autoIndexingDefault": true,
                "sourcePolicies": [
                  {
                    "sourceCategory": "products",
                    "enabled": true,
                    "manualIndexAllowed": true,
                    "manualReindexAllowed": true,
                    "autoIndexingEnabled": true,
                    "createTriggerEnabled": true,
                    "deleteTriggerEnabled": true,
                    "updateTriggerMode": "DELTA",
                    "selectedIndexedFields": ["products.title"],
                    "debounceWindowSeconds": 30,
                    "minimumRunIntervalSeconds": 60
                  }
                ],
                "updatedBy": "platform-admin",
                "updatedAt": "2026-04-18T10:05:00Z"
              },
              "effectiveIndexedFields": [
                {
                  "fieldKey": "products.title",
                  "sourceCategory": "products",
                  "entityType": "product",
                  "sourceField": "title",
                  "label": "Product title",
                  "selectableForTriggerPolicy": true
                }
              ],
              "automation": {
                "autoIndexingHealthy": true,
                "queuedEvents": 0,
                "leasedEvents": 0,
                "dispatchedEvents": 1,
                "skippedEvents": 0,
                "failedEvents": 0,
                "deadLetteredEvents": 0,
                "lastAutoEventAt": "2026-04-18T10:04:00Z",
                "lastSuccessfulAutoIndexAt": "2026-04-18T10:04:30Z",
                "lastFailedAutoIndexAt": null,
                "lastAutoRunId": "run-1",
                "degradedReasons": []
              },
              "recentEvents": [
                {
                  "id": "evt-1",
                  "sourceCategory": "products",
                  "entityType": "product",
                  "sourceObjectId": "gid://shopify/Product/1",
                  "shopifyTopic": "PRODUCTS_UPDATE",
                  "operation": "UPSERT",
                  "status": "SUCCEEDED",
                  "triggerReason": "WEBHOOK",
                  "failureCode": null,
                  "coalescedRunId": "run-1",
                  "shopifyWebhookId": "wh-1",
                  "occurredAt": "2026-04-18T10:04:00Z",
                  "queuedAt": "2026-04-18T10:04:01Z",
                  "lastAttemptAt": "2026-04-18T10:04:10Z",
                  "completedAt": "2026-04-18T10:04:30Z",
                  "notes": "Indexed successfully."
                }
              ]
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void handleRunSourcePreflightRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        byte[] payload = "{\"shopDomain\":\"demo.myshopify.com\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private void handleRunSourcePreflightConflictRequest(HttpExchange exchange) throws IOException {
        String apiKey = exchange.getRequestHeaders().getFirst("X-BRIDGE-API-KEY");
        if (!"bridge-admin-key".equals(apiKey)) {
            exchange.sendResponseHeaders(401, -1);
            return;
        }
        byte[] payload = """
            {
              "timestamp": "2026-04-19T00:57:26Z",
              "success": false,
              "errorCode": "CONFLICT",
              "message": "Shopify source preflight requires persisted store credentials. Install or reconnect the app first."
            }
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(409, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }
}
