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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PlatformManagedProductProvisioningServiceTest {

    @Test
    void coolifySourceInputsUseApiCompatibleFormats() {
        assertThat(PlatformManagedProductProvisioningService.coolifyGitRepository("mahmoudashraf/AI-Fabric-Framework"))
            .isEqualTo("mahmoudashraf/AI-Fabric-Framework.git");
        assertThat(PlatformManagedProductProvisioningService.coolifyGitRepository("https://github.com/example/repo.git"))
            .isEqualTo("example/repo.git");
        assertThat(PlatformManagedProductProvisioningService.coolifyGitRepository("git@github.com:example/repo.git"))
            .isEqualTo("example/repo.git");
        assertThat(PlatformManagedProductProvisioningService.coolifyDockerfileLocation("product-services/mcp/deploy/Dockerfile"))
            .isEqualTo("/product-services/mcp/deploy/Dockerfile");
        assertThat(PlatformManagedProductProvisioningService.coolifyDockerfileLocation("/deploy/Dockerfile"))
            .isEqualTo("/deploy/Dockerfile");
        assertThatThrownBy(() -> PlatformManagedProductProvisioningService.coolifyGitRepository("https://gitlab.com/example/repo.git"))
            .isInstanceOf(RailwayProvisioningConfigurationException.class);
    }

    @Test
    void reconcileShopifyBridgeCreatesRailwayResourcesAndStoresResolvedUrls() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setDetailsJson("""
            {
              "shopifyBridgeBilling": {
                "mode": "SHOPIFY_APP_SUBSCRIPTION",
                "starterEnabled": true,
                "starterPlanName": "Loom Companion Starter",
                "starterPlanHandle": "loom-companion-starter",
                "starterAmount": "29.00",
                "starterCurrencyCode": "USD",
                "starterInterval": "EVERY_30_DAYS",
                "starterTrialDays": 7,
                "starterTest": true,
                "eliteEnabled": true,
                "elitePlanName": "Loom Companion Elite",
                "elitePlanHandle": "loom-companion-elite",
                "eliteAmount": "179.00",
                "eliteCurrencyCode": "USD",
                "eliteInterval": "EVERY_30_DAYS",
                "eliteTrialDays": 0,
                "eliteTest": true
              }
            }
            """);

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
        mockMcpGateway(serviceRepository, platformSecretService);
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
                "SHOPIFY_BRIDGE_MCP_GATEWAY_BASE_URL",
                "SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY",
                "SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY_HEADER",
                "SHOPIFY_BRIDGE_MCP_GATEWAY_EXECUTE_PATH",
                "SHOPIFY_BRIDGE_SHOPIFY_API_KEY",
                "SHOPIFY_BRIDGE_SHOPIFY_API_SECRET",
                "SHOPIFY_BRIDGE_WEBHOOK_SHARED_SECRET",
                "SHOPIFY_BRIDGE_BILLING_MODE",
                "SHOPIFY_BRIDGE_BILLING_STARTER_ENABLED",
                "SHOPIFY_BRIDGE_BILLING_ELITE_ENABLED",
                "SHOPIFY_BRIDGE_BILLING_ELITE_AMOUNT",
                "SHOPIFY_BRIDGE_BILLING_TEST"
            );
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_PUBLIC_BASE_URL".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("https://shopify-bridge-prod.up.railway.app");
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_BILLING_MODE".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("SHOPIFY_APP_SUBSCRIPTION");
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_BILLING_ELITE_ENABLED".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("true");
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_BILLING_ELITE_AMOUNT".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("179.00");
    }

    @Test
    void reconcileCoolifyManagedShopifyBridgeDoesNotUseRailwayLifecycle() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setBaseUrl("https://shopify-bridge-prod.46.225.162.106.sslip.io");
        service.setStatus("CREATED");
        service.setDetailsJson("""
            {
              "providerType": "COOLIFY",
              "targetProfileId": "dtp-coolify-production",
              "coolifyApplicationUuid": "coolify-app-123"
            }
            """);

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        PlatformManagedProductServiceRepository serviceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(serviceService.getService("shopify-bridge-prod")).thenReturn(summary(service));
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretService.isSecretPresent("MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY")).thenReturn(true);
        when(platformSecretService.resolveSecret("MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY")).thenReturn("bridge-secret");
        mockMcpGateway(serviceRepository, platformSecretService);

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

        PlatformManagedProductServiceSummary summary = provisioningService.reconcile("shopify-bridge-prod");

        assertThat(summary.serviceRef()).isEqualTo("shopify-bridge-prod");
        assertThat(service.getStatus()).isEqualTo("ACTIVE");
        verify(serviceRepository).save(service);
        verifyNoInteractions(railwayGraphqlClient);
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

    @Test
    void defaultShopifySecretNamesAreResolvedWhenProvisioningPropertiesDoNotOverrideThem() {
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
        mockMcpGateway(serviceRepository, platformSecretService);
        when(platformSecretService.resolveSecret("SHOPIFY_APP_API_KEY")).thenReturn("shopify-api-key");
        when(platformSecretService.resolveSecret("SHOPIFY_APP_API_SECRET")).thenReturn("shopify-api-secret");
        when(platformSecretService.resolveSecret("SHOPIFY_WEBHOOK_SHARED_SECRET")).thenReturn(null);
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
                null,
                null,
                null,
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

        provisioningService.reconcile("shopify-bridge-prod");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RailwayGraphqlClient.RailwayEnvVarInput>> envCaptor = ArgumentCaptor.forClass(List.class);
        verify(railwayGraphqlClient).upsertVariables(eq("prj-123"), eq("env-123"), eq("svc-123"), envCaptor.capture());
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_SHOPIFY_API_KEY".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("shopify-api-key");
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_SHOPIFY_API_SECRET".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("shopify-api-secret");
        assertThat(envCaptor.getValue())
            .filteredOn(input -> "SHOPIFY_BRIDGE_WEBHOOK_SHARED_SECRET".equals(input.name()))
            .extracting(RailwayGraphqlClient.RailwayEnvVarInput::value)
            .containsExactly("shopify-api-secret");
    }

    @Test
    void refreshRailwayBindingFromWorkspaceRepairsDeletedProjectLinkage() {
        PlatformManagedProductServiceEntity service = productService("shopify-bridge-prod");
        service.setRailwayProjectId("deleted-project");
        service.setRailwayEnvironmentId("deleted-env");
        service.setRailwayServiceId("deleted-service");
        service.setBaseUrl("https://shopify-bridge-shopify-bridge-pr-production.up.railway.app");
        service.setStatus("ACTIVE");
        service.setDetailsJson("{\"lastDeploymentId\":\"dep-railway-123\"}");

        PlatformManagedProductServiceService serviceService = mock(PlatformManagedProductServiceService.class);
        PlatformManagedProductServiceRepository serviceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionRepository shopifyStoreConnectionRepository = mock(ShopifyStoreConnectionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        RailwayGraphqlClient railwayGraphqlClient = mock(RailwayGraphqlClient.class);

        when(serviceService.requireService("shopify-bridge-prod")).thenReturn(service);
        when(serviceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(railwayGraphqlClient.getProject("deleted-project"))
            .thenThrow(new RuntimeException("Railway GraphQL error: Project not found"));
        RailwayGraphqlClient.RailwayProjectSnapshot project = new RailwayGraphqlClient.RailwayProjectSnapshot(
            "prj-456",
            "loom-product-dev-shopify-bridge-prod",
            List.of(new RailwayGraphqlClient.RailwayEnvironmentSummary("env-456", "dev")),
            List.of(new RailwayGraphqlClient.RailwayServiceSummary("svc-456", "shopify-bridge-shopify-bridge-prod"))
        );
        when(railwayGraphqlClient.findProjectByName("ws-123", "loom-product-dev-shopify-bridge-prod")).thenReturn(project);
        when(railwayGraphqlClient.getServiceInstance("env-456", "svc-456"))
            .thenReturn(new RailwayGraphqlClient.RailwayServiceInstanceSummary(
                "inst-456",
                "svc-456",
                "shopify-bridge-shopify-bridge-prod",
                "product-services/shopify-bridge-service",
                "product-services/shopify-bridge-service/deploy/railway/Dockerfile",
                "/actuator/health",
                "http://shopify-bridge.internal",
                "TheBaseRepo",
                null
            ));

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

        PlatformManagedProductServiceEntity refreshed = provisioningService.refreshRailwayBindingFromWorkspace("shopify-bridge-prod");

        assertThat(refreshed.getRailwayProjectId()).isEqualTo("prj-456");
        assertThat(refreshed.getRailwayEnvironmentId()).isEqualTo("env-456");
        assertThat(refreshed.getRailwayServiceId()).isEqualTo("svc-456");
        assertThat(refreshed.getBaseUrl()).isEqualTo("https://shopify-bridge-shopify-bridge-pr-production.up.railway.app");
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

    private void mockMcpGateway(PlatformManagedProductServiceRepository serviceRepository,
                                PlatformSecretService platformSecretService) {
        PlatformManagedProductServiceEntity gateway = productService("mcp-execution-gateway");
        gateway.setId("psv-mcp-gateway");
        gateway.setDisplayName("MCP Execution Gateway");
        gateway.setProductFamily("MCP");
        gateway.setServiceKind("MCP_EXECUTION_GATEWAY_SERVICE");
        gateway.setBaseUrl("https://mcp-gateway.example.com");
        gateway.setSecretName("MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY");
        gateway.setStatus("ACTIVE");
        when(serviceRepository.findByServiceRefIgnoreCase("mcp-execution-gateway")).thenReturn(Optional.of(gateway));
        when(platformSecretService.resolveSecret("MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY")).thenReturn("gateway-secret");
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
            0,
            null
        );
    }
}
