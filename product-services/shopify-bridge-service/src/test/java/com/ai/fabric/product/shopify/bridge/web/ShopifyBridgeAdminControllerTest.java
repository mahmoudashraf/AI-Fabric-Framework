package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageEventCountSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageRoiSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSurfaceSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageTopQuerySummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeWebhookSubscriptionOverview;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeInstallOverview;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeStoreOverview;
import com.ai.fabric.product.shopify.bridge.diagnostics.service.ShopifyBridgeDiagnosticsService;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordBillingStateRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationAutomationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourcePageResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourceRecord;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreAdminService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionDiagnosticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.service-ref=shopify-bridge-test",
    "shopify.bridge.platform-base-url=https://platform.example.com",
    "shopify.bridge.platform-admin-api-key=platform-admin-key"
})
@AutoConfigureMockMvc
class ShopifyBridgeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyBridgeDiagnosticsService diagnosticsService;

    @MockBean
    private ShopifyBridgeStoreAdminService storeAdminService;

    @MockBean
    private ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService;

    @MockBean
    private ShopifyBridgeActionExecutionService actionExecutionService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void adminOverviewRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unmappedApiRoutesAreDeniedByDefault() throws Exception {
        mockMvc.perform(get("/api/internal/unknown"))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminOverviewReturnsDiagnosticsWhenApiKeyMatches() throws Exception {
        when(diagnosticsService.overview()).thenReturn(new ShopifyBridgeOverviewResponse(
            "Shopify Bridge Service",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://platform.example.com",
            "https://bridge.example.com",
            true,
            true,
            true,
            "READY",
            Instant.parse("2026-04-18T10:00:00Z"),
            new ShopifyBridgeInstallOverview(10, 8, 2, 7, Instant.parse("2026-04-18T10:10:00Z"), Instant.parse("2026-04-18T09:00:00Z")),
            new ShopifyBridgeStoreOverview("READY", "Platform store mappings resolved successfully.", 6, 3, 2, 1, 1, Instant.parse("2026-04-18T10:15:00Z")),
            new ShopifyBridgeWebhookSubscriptionOverview("READY", "Diagnostics available.", "https://bridge.example.com/api/webhooks/shopify", 9, List.of("APP_UNINSTALLED")),
            new ShopifyBridgeBillingSummary(
                "FREE",
                "FREE",
                "Companion Free",
                "ACTIVE",
                false,
                false,
                false,
                false,
                50,
                "DAILY",
                true,
                false,
                false,
                false,
                List.of(),
                List.of("ai-search"),
                List.of(),
                "Free mode."
            ),
            new ShopifyBridgeUsageOverview(Instant.parse("2026-04-18T10:20:00Z"), Instant.parse("2026-04-18T10:18:00Z"), 1, 2, 4, 9, List.of(), List.of()),
            List.of("managed-service-health"),
            List.of()
        ));
        mockMvc.perform(get("/api/admin/overview").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceRef").value("shopify-bridge-test"))
            .andExpect(jsonPath("$.platformBaseUrl").value("https://platform.example.com"))
            .andExpect(jsonPath("$.adminApiKeyConfigured").value(true))
            .andExpect(jsonPath("$.runtimeTrustedBackendApiKeyConfigured").value(true))
            .andExpect(jsonPath("$.runtimePrivateAssertionSigningKeyConfigured").value(true))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.installs.totalCount").value(10))
            .andExpect(jsonPath("$.stores.readyForGoLiveCount").value(3))
            .andExpect(jsonPath("$.webhookSubscriptions.expectedCount").value(9))
            .andExpect(jsonPath("$.billing.mode").value("FREE"))
            .andExpect(jsonPath("$.usage.totalToday").value(4));
    }

    @Test
    void adminWebhookSubscriptionsAreReturnedWhenApiKeyMatches() throws Exception {
        when(webhookSubscriptionDiagnosticsService.forShop("alpha.myshopify.com")).thenReturn(new ShopifyWebhookSubscriptionStatusSummary(
            "alpha.myshopify.com",
            "DEGRADED",
            "One topic is missing.",
            "https://bridge.example.com/api/webhooks/shopify",
            9,
            8,
            1,
            0,
            Instant.parse("2026-04-18T10:25:00Z"),
            List.of(new ShopifyWebhookSubscriptionTopicStatusSummary(
                "PRODUCTS_UPDATE",
                "loom-products-update",
                "MISSING",
                null,
                null,
                null,
                "Required Shopify webhook subscription is missing."
            ))
        ));

        mockMvc.perform(get("/api/admin/stores/alpha.myshopify.com/webhook-subscriptions").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.topics[0].topic").value("PRODUCTS_UPDATE"));
    }

    @Test
    void adminWebhookSubscriptionsCanBeRepairedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.repairWebhookSubscriptions("alpha.myshopify.com")).thenReturn(new ShopifyWebhookSubscriptionStatusSummary(
            "alpha.myshopify.com",
            "READY",
            "All required Shopify webhook subscriptions are present.",
            "https://bridge.example.com/api/webhooks/shopify",
            9,
            9,
            0,
            0,
            Instant.parse("2026-04-18T10:25:00Z"),
            List.of(new ShopifyWebhookSubscriptionTopicStatusSummary(
                "APP_SCOPES_UPDATE",
                "loom-app-scopes-update",
                "READY",
                "gid://shopify/WebhookSubscription/1",
                "loom-app-scopes-update",
                "https://bridge.example.com/api/webhooks/shopify",
                "Ready."
            ))
        ));

        mockMvc.perform(post("/api/admin/stores/alpha.myshopify.com/webhook-subscriptions/repair").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.topics[0].topic").value("APP_SCOPES_UPDATE"));

        verify(storeAdminService).repairWebhookSubscriptions("alpha.myshopify.com");
    }

    @Test
    void adminBillingSummaryIsReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.billingSummary("alpha.myshopify.com")).thenReturn(new ShopifyBridgeBillingSummary(
            "PAID",
            "STARTER",
            "Companion Starter",
            "READY_FOR_APPROVAL",
            true,
            true,
            false,
            false,
            null,
            null,
            false,
            true,
            false,
            true,
            List.of("launch-review"),
            List.of("ai-search"),
            List.of(
                new ShopifyBridgeBillingPlanSummary(
                    "FREE",
                    "Loom Companion Free",
                    null,
                    null,
                    null,
                    true,
                    true,
                    false,
                    false,
                    50,
                    "DAILY",
                    true,
                    false,
                    false,
                    false,
                    List.of(),
                    List.of("ai-search"),
                    "Free tier is always available."
                ),
                new ShopifyBridgeBillingPlanSummary(
                    "ELITE",
                    "Loom Companion Elite",
                    "179.00",
                    "USD",
                    "EVERY_30_DAYS",
                    false,
                    true,
                    true,
                    true,
                    null,
                    "HOURLY",
                    false,
                    true,
                    true,
                    true,
                    List.of("guided-commerce", "order-self-service"),
                    List.of("ai-search", "comparison"),
                    "Elite guided commerce is available for merchant approval."
                )
            ),
            "Merchant approval is required before go-live."
        ));

        mockMvc.perform(get("/api/admin/stores/alpha.myshopify.com/billing-summary").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("PAID"))
            .andExpect(jsonPath("$.tierKey").value("STARTER"))
            .andExpect(jsonPath("$.planName").value("Companion Starter"))
            .andExpect(jsonPath("$.status").value("READY_FOR_APPROVAL"))
            .andExpect(jsonPath("$.merchantApprovalRequired").value(true))
            .andExpect(jsonPath("$.launchBlocked").value(true))
            .andExpect(jsonPath("$.requiresExplicitConfirmation").value(false))
            .andExpect(jsonPath("$.auditTrailAvailable").value(true))
            .andExpect(jsonPath("$.actionPackages[0]").value("launch-review"))
            .andExpect(jsonPath("$.availablePlans[1].tierKey").value("ELITE"))
            .andExpect(jsonPath("$.availablePlans[1].requiresExplicitConfirmation").value(true))
            .andExpect(jsonPath("$.availablePlans[1].auditTrailAvailable").value(true))
            .andExpect(jsonPath("$.availablePlans[1].actionPackages[0]").value("guided-commerce"));
    }

    @Test
    void adminBillingStateRecordUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordBillingState(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordBillingStateRequest.class)
        )).thenReturn(new ShopifyBridgeBillingSummary(
            "FREE",
            "ELITE",
            "Loom Companion Elite",
            "ACTIVE",
            false,
            false,
            true,
            true,
            null,
            "HOURLY",
            false,
            true,
            true,
            true,
            List.of("guided-commerce", "order-self-service"),
            List.of("ai-search", "comparison", "order-lookup"),
            List.of(),
            "Elite tier is active for this store from recorded Shopify billing state."
        ));

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/billing-state")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"tierKey":"ELITE","status":"ACTIVE","reason":"live test activation"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tierKey").value("ELITE"))
            .andExpect(jsonPath("$.chatFallbackEnabled").value(true))
            .andExpect(jsonPath("$.actionCapable").value(true));

        verify(storeAdminService).recordBillingState(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordBillingStateRequest.class)
        );
    }

    @Test
    void adminUsageSummaryIsReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.usageSummary("alpha.myshopify.com")).thenReturn(new ShopifyBridgeUsageSummary(
            "alpha.myshopify.com",
            Instant.parse("2026-04-18T12:00:00Z"),
            Instant.parse("2026-04-18T11:59:00Z"),
            4,
            12,
            List.of(new ShopifyBridgeUsageEventCountSummary("STOREFRONT_QUERY", 4)),
            List.of(new ShopifyBridgeUsageEventCountSummary("STOREFRONT_QUERY", 12)),
            List.of(new ShopifyBridgeUsageSurfaceSummary("ai-search", "AI search", 2)),
            List.of(new ShopifyBridgeUsageSurfaceSummary("launcher", "Chat launcher", 7)),
            List.of(new ShopifyBridgeUsageTopQuerySummary(
                "launcher",
                "Chat launcher",
                "What is your return policy?",
                3,
                Instant.parse("2026-04-18T11:55:00Z")
            )),
            List.of(new ShopifyBridgeUsageTopQuerySummary(
                "launcher",
                "Chat launcher",
                "What is your return policy?",
                3,
                Instant.parse("2026-04-18T11:55:00Z")
            )),
            List.of(new ShopifyBridgeUsageTopQuerySummary(
                "launcher",
                "Chat launcher",
                "What is your return policy?",
                3,
                Instant.parse("2026-04-18T11:55:00Z")
            )),
            List.of(),
            new ShopifyBridgeUsageRoiSummary(
                "PROVING_VALUE",
                "Companion is generating credible shopper-assist and decision-support signal across real storefront surfaces.",
                7,
                2,
                1,
                0,
                0,
                2,
                List.of("Chat launcher", "AI search"),
                List.of("Promote product insight, FAQ, or comparison placements on product pages so Companion proves decision support, not only discovery.")
            )
        ));

        mockMvc.perform(get("/api/admin/stores/alpha.myshopify.com/usage-summary").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.last7DaySurfaceUsage[0].surfaceId").value("launcher"))
            .andExpect(jsonPath("$.topQuestionsLast7Days[0].queryText").value("What is your return policy?"))
            .andExpect(jsonPath("$.unansweredQuestionsLast7Days[0].queryText").value("What is your return policy?"))
            .andExpect(jsonPath("$.actionIntentQuestionsLast7Days[0].queryText").value("What is your return policy?"))
            .andExpect(jsonPath("$.last7DaySurfaceJourneys").isArray())
            .andExpect(jsonPath("$.roiSummary.status").value("PROVING_VALUE"))
            .andExpect(jsonPath("$.roiSummary.strongestSurfaceLabels[0]").value("Chat launcher"));
    }

    @Test
    void adminRecentGovernedActionsAreReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recentGovernedActions("alpha.myshopify.com", 5)).thenReturn(List.of(
            new ShopifyBridgeGovernedActionAuditSummary(
                "sga-1",
                "ADD_TO_CART",
                "guided-commerce",
                "product-insight",
                "PRODUCT",
                "travel-pack",
                "Travel Pack",
                "202",
                1,
                null,
                1,
                true,
                true,
                "shop…0001",
                "COMPLETED",
                "Guided add-to-cart completed.",
                Instant.parse("2026-04-23T12:00:00Z"),
                Instant.parse("2026-04-23T12:05:00Z"),
                Instant.parse("2026-04-23T12:00:03Z")
            )
        ));

        mockMvc.perform(get("/api/admin/stores/alpha.myshopify.com/actions/recent?limit=5").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].actionType").value("ADD_TO_CART"))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void adminVectorizationSummaryIsReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.vectorizationSummary("alpha.myshopify.com")).thenReturn(new ShopifyBridgeStoreVectorizationSummary(
            "alpha.myshopify.com",
            "dep-1",
            true,
            List.of("products", "policies"),
            List.of("product", "policy"),
            List.of("mkp-template-shopify-companion"),
            List.of("mkp-template-shopify-companion"),
            List.of(),
            List.of(),
            false,
            true,
            "vcn-1",
            "READY",
            "REST_API",
            true,
            "vpl-1",
            "ACTIVE",
            true,
            "vrr-1",
            "ACTIVE",
            false,
            "IDLE",
            "PLATFORM_MANAGED_AUTO",
            "CURRENT",
            true,
            List.of(),
            null,
            null,
            List.of(),
            new ShopifyBridgeStoreVectorizationAutomationSummary(
                true,
                0,
                0,
                3,
                0,
                0,
                0,
                Instant.parse("2026-04-18T11:00:00Z"),
                Instant.parse("2026-04-18T11:05:00Z"),
                null,
                "vrn-1",
                List.of()
            ),
            List.of()
        ));

        mockMvc.perform(get("/api/admin/stores/alpha.myshopify.com/vectorization").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.syncState").value("CURRENT"))
            .andExpect(jsonPath("$.automation.autoIndexingHealthy").value(true));
    }

    @Test
    void adminVectorizationSourcePageIsReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.vectorizationSourcePage("alpha.myshopify.com", "product", null, 25))
            .thenReturn(new ShopifyBridgeVectorizationSourcePageResponse(
                "alpha.myshopify.com",
                "product",
                3,
                true,
                "collections|",
                List.of(new ShopifyBridgeVectorizationSourceRecord(
                    "gid://shopify/Product/1",
                    "2026-04-19T10:00:00Z",
                    "Trail Shoe",
                    "Breathable trail shoe",
                    "products",
                    "product",
                    "https://alpha.myshopify.com/products/trail-shoe",
                    "trail-shoe",
                    "Loom",
                    "Shoes",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ))
            ));

        mockMvc.perform(
                get("/api/admin/stores/alpha.myshopify.com/vectorization-source/product")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .param("limit", "25")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.entityType").value("product"))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.items[0].title").value("Trail Shoe"))
            .andExpect(jsonPath("$.nextCursor").value("collections|"));
    }

    @Test
    void adminStoresAreReturnedWhenApiKeyMatches() throws Exception {
        when(storeAdminService.listStores()).thenReturn(List.of(sampleStore()));

        mockMvc.perform(get("/api/admin/stores").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$[0].productServiceRef").value("shopify-bridge-test"));
    }

    @Test
    void adminActionExecutionUsesActionServiceWhenApiKeyMatches() throws Exception {
        when(actionExecutionService.execute(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(ShopifyBridgeActionResult.ok("Products", java.util.Map.of("count", 1)));

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/actions/execute")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .header("X-Forwarded-For", "203.0.113.44, 10.0.0.10")
                    .header("User-Agent", "checkout-smoke/1.0")
                    .contentType("application/json")
                    .content("""
                        {"actionId":"list_products","params":{},"trace":{}}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Products"))
            .andExpect(jsonPath("$.data.count").value(1));

        ArgumentCaptor<ShopifyBridgeActionExecuteRequest> requestCaptor =
            ArgumentCaptor.forClass(ShopifyBridgeActionExecuteRequest.class);
        verify(actionExecutionService).execute(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            requestCaptor.capture()
        );
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().trace())
            .containsEntry("buyerIp", "203.0.113.44")
            .containsEntry("buyerUserAgent", "checkout-smoke/1.0");
    }

    @Test
    void adminActionExecutionReturnsFailureHttpStatusForActionFailures() throws Exception {
        when(actionExecutionService.execute(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(ShopifyBridgeActionResult.failure("NOT_FOUND", "No product was found."));

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/actions/execute")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"actionId":"check_availability","params":{"sku":"missing"},"trace":{}}
                        """)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("No product was found."))
            .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void adminBootstrapUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.bootstrap("alpha.myshopify.com")).thenReturn(new ShopifyBridgeStoreBootstrapResponse(
            "alpha.myshopify.com",
            "cust-1",
            "dep-1",
            "consumer-1",
            true,
            true,
            true,
            List.of("mkp-template-shopify-companion"),
            sampleStore()
        ));

        mockMvc.perform(post("/api/admin/stores/alpha.myshopify.com/bootstrap").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.createdDeployment").value(true))
            .andExpect(jsonPath("$.store.consumerId").value("consumer-1"));
    }

    @Test
    void adminRunSourcePreflightUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.runSourcePreflight("alpha.myshopify.com")).thenReturn(sampleStore());

        mockMvc.perform(post("/api/admin/stores/alpha.myshopify.com/run-source-preflight").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    @Test
    void adminSourcePreflightUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordSourcePreflight(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordSourcePreflightRequest.class)
        )).thenReturn(sampleStore());

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/source-preflight")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"categories":[{"category":"products","enabled":true,"status":"READY","itemCount":120,"message":"Products reachable"}]}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    @Test
    void adminSyncStatusUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordSyncStatus(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordSyncStatusRequest.class)
        )).thenReturn(sampleStore());

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/sync-status")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"status":"SYNCED","mode":"FULL","documentCount":128,"message":"Initial import completed"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    @Test
    void adminWidgetStatusUsesStoreServiceWhenApiKeyMatches() throws Exception {
        when(storeAdminService.recordWidgetStatus(
            org.mockito.ArgumentMatchers.eq("alpha.myshopify.com"),
            org.mockito.ArgumentMatchers.any(ShopifyBridgeRecordWidgetStatusRequest.class)
        )).thenReturn(sampleStore());

        mockMvc.perform(
                post("/api/admin/stores/alpha.myshopify.com/widget-status")
                    .header("X-BRIDGE-API-KEY", "test-admin-key")
                    .contentType("application/json")
                    .content("""
                        {"status":"ENABLED","channel":"THEME_APP_EXTENSION","message":"Theme embed enabled"}
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));
    }

    private ShopifyBridgeStoreSummary sampleStore() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-test",
            "Shopify Bridge Test",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "DRAFT",
            "consumer-1",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "PLATFORM_BOOTSTRAPPED",
            true,
            true,
            true,
            true,
            false,
            false,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products",
                true
            ),
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
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }
}
