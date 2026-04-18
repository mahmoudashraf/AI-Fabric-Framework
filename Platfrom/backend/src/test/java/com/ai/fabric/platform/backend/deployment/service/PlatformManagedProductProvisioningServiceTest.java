package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformManagedProductProvisioningServiceTest {

    @Test
    void reconcileShopifyBridgeCreatesRailwayResourcesAndStoresResolvedUrls() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        PlatformManagedProductServiceRepository serviceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(serviceService.getService("shopify-bridge-prod")).thenReturn(summary(service));
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(railwayGraphqlClient.findProjectByName("ws-123", "loom-product-dev-shopify-bridge-prod")).thenReturn(null);
        RailwayGraphqlClient.RailwayProjectSnapshot project = new RailwayGraphqlClient.RailwayProjectSnapshot(
            "prj-123",
            "loom-product-dev-shopify-bridge-prod",
            List.of(),
            List.of()
        );
        when(railwayGraphqlClient.createProject("ws-123", "loom-product-dev-shopify-bridge-prod", "dev")).thenReturn(project);
        when(railwayGraphqlClient.createEnvironment("prj-123", "dev")).thenReturn(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-123", "dev"));
        when(railwayGraphqlClient.createServiceFromRepository("prj-123", "shopify-bridge-shopify-bridge-prod", "TheBaseRepo", "Platform-V5"))
            .thenReturn(new RailwayGraphqlClient.RailwayServiceSummary("svc-123", "shopify-bridge-shopify-bridge-prod"));
        when(railwayGraphqlClient.connectServiceToRepository("svc-123", "TheBaseRepo", "Platform-V5"))
            .thenReturn(new RailwayGraphqlClient.RailwayServiceSummary("svc-123", "shopify-bridge-shopify-bridge-prod"));
        when(platformSecretService.isSecretPresent("MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY")).thenReturn(true);
        when(platformSecretService.resolveSecret("MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY")).thenReturn("bridge-secret");
        when(platformSecretService.resolveSecret("SHOPIFY_APP_API_KEY")).thenReturn("shopify-api-key");
        when(platformSecretService.resolveSecret("SHOPIFY_APP_API_SECRET")).thenReturn("shopify-api-secret");
        when(railwayGraphqlClient.hasStagedChanges("env-123")).thenReturn(false);
        when(railwayGraphqlClient.deployService("svc-123", "env-123")).thenReturn("dep-railway-123");
        when(railwayGraphqlClient.getDeployment("dep-railway-123"))
            .thenReturn(new RailwayGraphqlClient.RailwayDeploymentSummary("dep-railway-123", "SUCCESS", null, null, Instant.now().toString()));
        when(railwayGraphqlClient.getServiceInstance("env-123", "svc-123"))
            .thenReturn(new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-123",
                "svc-123",
                "shopify-bridge-shopify-bridge-prod",
                "product-services/shopify-bridge-service",
                "product-services/shopify-bridge-service/deploy/railway/Dockerfile",
                "/actuator/health",
                "http://shopify-bridge.internal",
                "TheBaseRepo",
                null
            ));
        when(railwayGraphqlClient.listServiceDomains("prj-123", "env-123", "svc-123"))
            .thenReturn(List.of(new RailwayGraphqlClient.RailwayServiceDomainSummary("dom-123", "shopify-bridge-prod.up.railway.app")));

        PlatformManagedProductProvisioningService provisioningService = new PlatformManagedProductProvisioningService(
            new PlatformProvisioningProperties(
                "RAILWAY",
                "https://backboard.railway.com/graphql/v2",
                "token",
                "TheBaseRepo",
                "Platform-V5",
                "dev",
                "ws-123",
                null,
                null,
                null,
                null,
                null,
                null,
                40,
                null,
                null,
                false,
                false,
                60_000,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
            ),
            new PlatformProductProvisioningProperties(
                null,
                null,
                null,
                null,
                null,
                "2026-04",
                "SHOPIFY_APP_API_KEY",
                "SHOPIFY_APP_API_SECRET",
                "",
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)
            ),
            new PlatformDeliveryProperties("https://platform.example.com", true, Duration.ofDays(1)),
            railwayGraphqlClient,
            platformSecretService,
            serviceRepository,
            shopifyStoreConnectionRepository,
            serviceService,
            platformAuditService,
            new ObjectMapper()
        );

        PlatformManagedProductServiceSummary summary = provisioningService.reconcile("shopify-bridge-prod");

        assertThat(summary.serviceRef()).isEqualTo("shopify-bridge-prod");
        assertThat(service.getRailwayProjectId()).isEqualTo("prj-123");
        assertThat(service.getRailwayEnvironmentId()).isEqualTo("env-123");
        assertThat(service.getRailwayServiceId()).isEqualTo("svc-123");
        assertThat(service.getBaseUrl()).isEqualTo("https://shopify-bridge-prod.up.railway.app");
        assertThat(service.getPrivateNetworkUrl()).isEqualTo("http://shopify-bridge.internal");
        assertThat(service.getStatus()).isEqualTo("ACTIVE");
        assertThat(service.getSecretName()).isEqualTo("MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY");
        verify(railwayGraphqlClient).updateServiceInstance(
            eq("svc-123"),
            eq("env-123"),
            eq("product-services/shopify-bridge-service"),
            eq("product-services/shopify-bridge-service/deploy/railway/Dockerfile"),
            eq("/actuator/health"),
            eq(1)
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RailwayGraphqlClient.RailwayEnvVarInput>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(railwayGraphqlClient).upsertVariables(eq("prj-123"), eq("env-123"), eq("svc-123"), envCaptor.capture());
        assertThat(envCaptor.getValue())
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::name)
            .contains(
                "SHOPIFY_BRIDGE_SHARED_SECRET",
                "SHOPIFY_BRIDGE_PLATFORM_ADMIN_API_KEY",
                "SHOPIFY_BRIDGE_PUBLIC_BASE_URL",
                "SHOPIFY_BRIDGE_PLATFORM_BASE_URL",
                "SHOPIFY_BRIDGE_ADMIN_API_VERSION",
                "SHOPIFY_BRIDGE_SHOPIFY_API_KEY",
                "SHOPIFY_BRIDGE_SHOPIFY_API_SECRET",
                "SHOPIFY_BRIDGE_WEBHOOK_SHARED_SECRET"
            );
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_PUBLIC_BASE_URL".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("https://shopify-bridge-prod.up.railway.app");
    }

    @Test
    void decommissionRejectsServicesWithDependentStores() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setRailwayProjectId("prj-123");

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        PlatformManagedProductServiceRepository serviceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(shopifyStoreConnectionRepository.countByProductServiceId("psv-123")).thenReturn(1L);

        PlatformManagedProductProvisioningService provisioningService = new PlatformManagedProductProvisioningService(
            new PlatformProvisioningProperties(null, null, null, null, null, null, "ws-123", null, null, null, null, null, null, 32, null, null, false, false, 60_000, Duration.ofSeconds(1), Duration.ofSeconds(5)),
            new PlatformProductProvisioningProperties(null, null, null, null, null, null, null, null, null, Duration.ofSeconds(1), Duration.ofSeconds(5)),
            new PlatformDeliveryProperties("https://platform.example.com", true, Duration.ofDays(1)),
            railwayGraphqlClient,
            platformSecretService,
            serviceRepository,
            shopifyStoreConnectionRepository,
            serviceService,
            platformAuditService,
            new ObjectMapper()
        );

        assertThatThrownBy(() -> provisioningService.decommission("shopify-bridge-prod"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("dependent store mapping");

        verify(railwayGraphqlClient, never()).deleteProject("prj-123");
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
        service.setEnvironmentScope("dev");
        service.setDesiredReplicas(1);
        service.setActualReplicas(0);
        service.setMinReplicas(1);
        service.setMaxReplicas(3);
        service.setHealthPath("/actuator/health");
        service.setServiceRoot("product-services/shopify-bridge-service");
        service.setDockerfilePath("product-services/shopify-bridge-service/deploy/railway/Dockerfile");
        service.setStatus("CREATED");
        service.setDetailsJson("{}");
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private PlatformManagedProductServiceSummary summary(PlatformManagedProductServiceEntity service) {
        return new PlatformManagedProductServiceSummary(
            service.getId(),
            service.getServiceRef(),
            service.getDisplayName(),
            service.getProductFamily(),
            service.getServiceKind(),
            service.getDeploymentMode(),
            service.getTenantMode(),
            service.getEnvironmentScope(),
            service.getDeploymentId(),
            service.getRailwayProjectId(),
            service.getRailwayEnvironmentId(),
            service.getRailwayServiceId(),
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
    }
}
