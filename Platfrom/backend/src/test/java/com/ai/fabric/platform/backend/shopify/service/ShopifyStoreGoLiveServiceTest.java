package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.productservice.model.PlatformManagedProductServiceStoreSupportReadinessSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductAdminService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreReadinessSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreGoLiveServiceTest {

    @Test
    void goLivePublishesAndAppliesDeployment() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        PlatformManagedProductAdminService productAdminService = mock(PlatformManagedProductAdminService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("READY");
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));
        when(productAdminService.getStoreSupportReadiness("shopify-bridge-prod", "alpha.myshopify.com"))
            .thenReturn(supportReadiness("READY", true, true));
        when(deploymentService.getActiveDraftForDeployment("dep-1")).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "DRAFT",
            currentActionsConfig(),
            null,
            staleRoutingConfig(),
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        ));
        when(deploymentService.updateDraft(eq("drf-1"), any(UpdateDeploymentDraftRequest.class))).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "MODIFIED",
            currentActionsConfig(),
            null,
            staleRoutingConfig(),
            null,
            JsonNodeFactory.instance.objectNode().put("authzMode", "ALLOW_VERIFIED"),
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:01:00Z")
        ));
        when(deploymentService.publishDraft("drf-1")).thenReturn(new DeploymentVersionSummary(
            "ver-1",
            "dep-1",
            "drf-1",
            "v4",
            "PUBLISHED",
            "hash-1",
            false,
            Instant.parse("2026-04-18T10:05:00Z")
        ));
        when(deploymentService.applyVersion("dep-1", "ver-1")).thenReturn(new DeploymentReleaseSummary(
            "rel-1",
            "dep-1",
            "ver-1",
            "APPLY_REQUESTED",
            "PENDING",
            "QUEUED",
            "RAILWAY",
            null,
            null,
            null,
            null,
            "queue_release",
            "Apply request accepted and queued.",
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:05:10Z"),
            Instant.parse("2026-04-18T10:05:10Z"),
            Instant.parse("2026-04-18T10:05:10Z")
        ));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary("alpha.myshopify.com", "GO_LIVE_REQUESTED"));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            draftCompilerService,
            productServiceRepository,
            productAdminService,
            connectionService,
            auditService
        );

        ShopifyStoreConnectionSummary result = service.goLive("alpha.myshopify.com");

        assertThat(result.onboardingStatus()).isEqualTo("GO_LIVE_REQUESTED");
        assertThat(store.getOnboardingStatus()).isEqualTo("GO_LIVE_REQUESTED");
        verify(deploymentService).updateDraft(eq("drf-1"), argThat(request ->
            matchesShopifyCompanionSecurityDefaults(request)
        ));
        verify(deploymentService).updateDraft(eq("drf-1"), argThat(this::matchesShopifyBridgeRoutingDefaults));
        verify(draftCompilerService).syncDeploymentDraft("dep-1");
        verify(deploymentService).publishDraft("drf-1");
        verify(deploymentService).applyVersion("dep-1", "ver-1");
    }

    @Test
    void goLiveRepairsMissingBridgePrivateRuntimeIssuerEvenWhenAllowVerifiedAlreadySet() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        PlatformManagedProductAdminService productAdminService = mock(PlatformManagedProductAdminService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("READY");
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));
        when(productAdminService.getStoreSupportReadiness("shopify-bridge-prod", "alpha.myshopify.com"))
            .thenReturn(supportReadiness("READY", true, true));

        ObjectNode securityConfig = JsonNodeFactory.instance.objectNode();
        securityConfig.put("authzMode", "ALLOW_VERIFIED");
        when(deploymentService.getActiveDraftForDeployment("dep-1")).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "DRAFT",
            currentActionsConfig(),
            null,
            staleRoutingConfig(),
            null,
            securityConfig,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        ));
        when(deploymentService.updateDraft(eq("drf-1"), any(UpdateDeploymentDraftRequest.class))).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "MODIFIED",
            currentActionsConfig(),
            null,
            staleRoutingConfig(),
            null,
            securityConfig,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:01:00Z")
        ));
        when(deploymentService.publishDraft("drf-1")).thenReturn(new DeploymentVersionSummary(
            "ver-1",
            "dep-1",
            "drf-1",
            "v4",
            "PUBLISHED",
            "hash-1",
            false,
            Instant.parse("2026-04-18T10:05:00Z")
        ));
        when(deploymentService.applyVersion("dep-1", "ver-1")).thenReturn(new DeploymentReleaseSummary(
            "rel-1",
            "dep-1",
            "ver-1",
            "APPLY_REQUESTED",
            "PENDING",
            "QUEUED",
            "RAILWAY",
            null,
            null,
            null,
            null,
            "queue_release",
            "Apply request accepted and queued.",
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:05:10Z"),
            Instant.parse("2026-04-18T10:05:10Z"),
            Instant.parse("2026-04-18T10:05:10Z")
        ));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary("alpha.myshopify.com", "GO_LIVE_REQUESTED"));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            draftCompilerService,
            productServiceRepository,
            productAdminService,
            connectionService,
            auditService
        );

        service.goLive("alpha.myshopify.com");

        verify(deploymentService).updateDraft(eq("drf-1"), argThat(this::matchesShopifyCompanionSecurityDefaults));
    }

    @Test
    void goLiveRejectsWhenStoreIsNotPreflightReady() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        PlatformManagedProductAdminService productAdminService = mock(PlatformManagedProductAdminService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store("BLOCKED")));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary(
            "alpha.myshopify.com",
            "PREFLIGHT_READY",
            new ShopifyStoreReadinessSummary(
                "BLOCKED",
                false,
                false,
                java.util.List.of("Shopify source readiness is not READY yet."),
                java.util.List.of("Shopify source readiness is not READY yet."),
                java.util.List.of("Run source preflight and resolve any blocked Shopify source categories.")
            )
        ));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            draftCompilerService,
            productServiceRepository,
            productAdminService,
            connectionService,
            auditService
        );

        assertThatThrownBy(() -> service.goLive("alpha.myshopify.com"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("source readiness is not READY");
    }

    @Test
    void goLiveRejectsWhenSupportScopeGrantIsPending() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        PlatformManagedProductAdminService productAdminService = mock(PlatformManagedProductAdminService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store("READY")));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary("alpha.myshopify.com", "PREFLIGHT_READY"));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));
        when(productAdminService.getStoreSupportReadiness("shopify-bridge-prod", "alpha.myshopify.com"))
            .thenReturn(supportReadiness("PENDING_SCOPE_GRANT", false, true));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            draftCompilerService,
            productServiceRepository,
            productAdminService,
            connectionService,
            auditService
        );

        assertThatThrownBy(() -> service.goLive("alpha.myshopify.com"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("scope approval");
    }

    @Test
    void goLiveRejectsWhenMerchantHandoffIsNotConfigured() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentMarketplaceDraftCompilerService draftCompilerService = mock(DeploymentMarketplaceDraftCompilerService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        PlatformManagedProductAdminService productAdminService = mock(PlatformManagedProductAdminService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store("READY")));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary("alpha.myshopify.com", "PREFLIGHT_READY"));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));
        when(productAdminService.getStoreSupportReadiness("shopify-bridge-prod", "alpha.myshopify.com"))
            .thenReturn(supportReadiness("READY", true, true, false));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            draftCompilerService,
            productServiceRepository,
            productAdminService,
            connectionService,
            auditService
        );

        assertThatThrownBy(() -> service.goLive("alpha.myshopify.com"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("support handoff");
    }

    private ShopifyStoreConnectionEntity store(String sourceReadinessStatus) {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-1");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setProductServiceId("ps-1");
        entity.setInstallStatus("INSTALLED");
        entity.setSourceReadinessStatus(sourceReadinessStatus);
        entity.setOnboardingStatus("PREFLIGHT_READY");
        entity.setCustomerId("cust-1");
        entity.setDeploymentId("dep-1");
        entity.setConsumerId("consumer-alpha");
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        return entity;
    }

    private ShopifyStoreConnectionSummary summary(String shopDomain, String onboardingStatus) {
        return summary(
            shopDomain,
            onboardingStatus,
            new ShopifyStoreReadinessSummary(
                "READY_FOR_GO_LIVE",
                true,
                false,
                java.util.List.of(),
                java.util.List.of("No verified deployment release exists yet."),
                java.util.List.of("Request go-live to publish, apply, and verify the deployment.")
            )
        );
    }

    private ShopifyStoreConnectionSummary summary(String shopDomain,
                                                  String onboardingStatus,
                                                  ShopifyStoreReadinessSummary readiness) {
        return new ShopifyStoreConnectionSummary(
            "shp-1",
            shopDomain,
            "Alpha",
            "ps-1",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "APPLY_REQUESTED",
            "consumer-alpha",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "READY",
            "NOT_ENABLED",
            onboardingStatus,
            true,
            true,
            true,
            true,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            readiness,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        );
    }

    private boolean matchesShopifyBridgeRoutingDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.routingConfig() instanceof ObjectNode routing)) {
            return false;
        }
        ObjectNode upstream = (ObjectNode) routing.path("connector").path("upstream");
        ObjectNode auth = (ObjectNode) upstream.path("auth");
        ObjectNode actions = (ObjectNode) routing.path("actions");
        ObjectNode listProducts = (ObjectNode) routing.path("actions").path("list_products");
        ObjectNode addProductToCart = (ObjectNode) routing.path("actions").path("add_product_to_cart");
        ObjectNode requestBody = (ObjectNode) listProducts.path("request").path("body");
        ObjectNode addRequestBody = (ObjectNode) addProductToCart.path("request").path("body");
        return "https://shopify-bridge.example.com".equals(upstream.path("base-url").asText())
            && "API_KEY".equals(auth.path("type").asText())
            && "X-BRIDGE-API-KEY".equals(auth.path("header").asText())
            && "${SHOPIFY_BRIDGE_SHARED_SECRET}".equals(auth.path("value").asText())
            && !actions.has("find_similar_products")
            && !actions.has("compare_products")
            && actions.has("custom_unrelated_action")
            && "POST".equals(listProducts.path("method").asText())
            && "/api/admin/stores/alpha.myshopify.com/actions/execute".equals(listProducts.path("path").asText())
            && "POST".equals(addProductToCart.path("method").asText())
            && "/api/admin/stores/alpha.myshopify.com/actions/execute".equals(addProductToCart.path("path").asText())
            && "{{actionId}}".equals(requestBody.path("actionId").asText())
            && "{{params}}".equals(requestBody.path("params").asText())
            && "{{idempotencyKey}}".equals(requestBody.path("idempotencyKey").asText())
            && "{{trace}}".equals(requestBody.path("trace").asText())
            && "{{actionId}}".equals(addRequestBody.path("actionId").asText())
            && "{{params}}".equals(addRequestBody.path("params").asText());
    }

    private ObjectNode currentActionsConfig() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        var actions = root.putArray("actions");
        actions.addObject().put("name", "list_products");
        actions.addObject().put("name", "search_products");
        actions.addObject().put("name", "get_product_details");
        actions.addObject().put("name", "check_availability");
        actions.addObject().put("name", "get_policy");
        actions.addObject().put("name", "add_product_to_cart");
        actions.addObject().put("name", "add_to_cart");
        actions.addObject().put("name", "update_cart_quantity");
        return root;
    }

    private ObjectNode staleRoutingConfig() {
        ObjectNode routing = JsonNodeFactory.instance.objectNode();
        ObjectNode actions = routing.putObject("actions");
        managedShopifyBridgeRoute(actions.putObject("find_similar_products"));
        managedShopifyBridgeRoute(actions.putObject("compare_products"));
        ObjectNode unrelated = actions.putObject("custom_unrelated_action");
        unrelated.put("method", "POST");
        unrelated.put("path", "/api/custom/actions");
        return routing;
    }

    private void managedShopifyBridgeRoute(ObjectNode action) {
        action.put("method", "POST");
        action.put("path", "/api/admin/stores/alpha.myshopify.com/actions/execute");
        ObjectNode body = action.putObject("request").putObject("body");
        body.put("actionId", "{{actionId}}");
        body.put("params", "{{params}}");
        body.put("idempotencyKey", "{{idempotencyKey}}");
        body.put("trace", "{{trace}}");
    }

    private boolean matchesShopifyCompanionSecurityDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.securityConfig() instanceof ObjectNode security)) {
            return false;
        }
        String issuers = security.path("privateRuntimeAcceptedIssuers").asText("");
        String audiences = security.path("privateRuntimeAcceptedAudiences").asText("");
        return "ALLOW_VERIFIED".equals(security.path("authzMode").asText())
            && issuers.contains("platform-consumer-bridge")
            && issuers.contains("platform-poc:SESSION")
            && audiences.contains("dep-1");
    }

    private PlatformManagedProductServiceEntity productService(String id) {
        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(id);
        entity.setBaseUrl("https://shopify-bridge.example.com/");
        entity.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        entity.setServiceRef("shopify-bridge-prod");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        return entity;
    }

    private PlatformManagedProductServiceStoreSupportReadinessSummary supportReadiness(String status,
                                                                                       boolean orderLookupSupported,
                                                                                       boolean scopesWebhookReady) {
        return supportReadiness(status, orderLookupSupported, scopesWebhookReady, true);
    }

    private PlatformManagedProductServiceStoreSupportReadinessSummary supportReadiness(String status,
                                                                                       boolean orderLookupSupported,
                                                                                       boolean scopesWebhookReady,
                                                                                       boolean merchantHandoffConfigured) {
        return new PlatformManagedProductServiceStoreSupportReadinessSummary(
            "alpha.myshopify.com",
            status,
            "READY".equals(status)
                ? "Customer-safe order lookup is ready for recent orders."
                : "Customer-safe order lookup is waiting for Shopify order-read scope approval on this store.",
            "READY".equals(status) ? "RECENT_ORDER_ONLY" : "SCOPE_APPROVAL",
            orderLookupSupported,
            orderLookupSupported,
            false,
            scopesWebhookReady,
            false,
            null,
            !orderLookupSupported,
            !orderLookupSupported ? "https://shopify-bridge.example.com/auth/shopify/install?shop=alpha.myshopify.com" : null,
            "INSTALLED",
            orderLookupSupported || !"READY".equals(status) ? "ELITE" : "FREE",
            "ACTIVE",
            orderLookupSupported ? java.util.List.of("read_orders") : java.util.List.of(),
            orderLookupSupported ? java.util.List.of() : java.util.List.of("read_orders"),
            java.util.List.of("Companion Free"),
            java.util.List.of(),
            new PlatformManagedProductServiceStoreSupportProfileSummary(
                merchantHandoffConfigured ? "support@example.com" : null,
                null,
                null,
                null,
                null,
                merchantHandoffConfigured
            ),
            merchantHandoffConfigured,
            merchantHandoffConfigured
                ? "Merchant support handoff is configured through support email."
                : "Merchant support handoff is not configured yet. Add a support email, contact URL, or help center URL before launch.",
            java.util.List.of(),
            java.util.List.of("ORDER_NUMBER_AND_EMAIL"),
            java.util.List.of("order-status", "tracking-link"),
            java.util.List.of("refunds")
        );
    }
}
