package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageEventCountSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageRoiSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSurfaceSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageTopQuerySummary;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalRequest;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalResponse;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessDecisionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgePartnerAccessRequestSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCapabilitySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationAutomationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationEventSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationIndexedFieldSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationPolicySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSourcePolicySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSettingsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeMerchantStoreService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import com.ai.fabric.product.shopify.bridge.playground.service.ShopifyMerchantPlaygroundService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPlacementSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret",
    "shopify.bridge.admin-api-key=test-admin-key"
})
@AutoConfigureMockMvc
class ShopifyMerchantControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyBridgeMerchantStoreService merchantStoreService;

    @MockBean
    private ShopifyMerchantPlaygroundService merchantPlaygroundService;

    @MockBean
    private ShopifyBridgeUsageService usageService;

    @Test
    void sessionRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/app/session"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionReturnsMerchantScopedStoreContext() throws Exception {
        when(merchantStoreService.session(any(), anyString())).thenReturn(new ShopifyBridgeMerchantSessionResponse(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z"),
            false,
            null,
            null,
            null,
            new ShopifyInstallRecordSummary(
                "alpha.myshopify.com",
                "INSTALLED",
                "https://alpha.myshopify.com",
                "gid://shopify/User/1",
                "embedded-host",
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                "read_products",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                false,
                Instant.parse("2026-04-18T00:00:00Z"),
                null,
                null,
                List.of(),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                null
            ),
            store()
        ));

        mockMvc.perform(get("/api/app/session")
                .header("Authorization", "Bearer " + token())
                .header("X-Shopify-Embedded-Host", "embedded-host"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.installRecord.status").value("INSTALLED"))
            .andExpect(jsonPath("$.installRecord.accessTokenSecretRef").value("MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA"))
            .andExpect(jsonPath("$.store.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).session(any(), anyString());
    }

    @Test
    void recentGovernedActionsUseMerchantSessionContext() throws Exception {
        when(merchantStoreService.recentGovernedActions(any(), anyInt())).thenReturn(List.of(
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

        mockMvc.perform(get("/api/app/store/actions/recent?limit=5").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].actionType").value("ADD_TO_CART"))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        verify(merchantStoreService).recentGovernedActions(any(), anyInt());
    }

    @Test
    void bootstrapUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.bootstrap(any(), anyString())).thenReturn(new ShopifyBridgeStoreBootstrapResponse(
            "alpha.myshopify.com",
            "cust-1",
            "dep-1",
            "consumer-1",
            true,
            true,
            true,
            List.of("mkp-template-shopify-companion"),
            store()
        ));

        mockMvc.perform(post("/api/app/store/bootstrap").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId").value("dep-1"));

        verify(merchantStoreService).bootstrap(any(), anyString());
    }

    @Test
    void sourcePreflightUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.runSourcePreflight(any(), anyString())).thenReturn(store());

        mockMvc.perform(post("/api/app/store/source-preflight").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).runSourcePreflight(any(), anyString());
    }

    @Test
    void goLiveUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.goLive(any(), anyString())).thenReturn(store());

        mockMvc.perform(post("/api/app/store/go-live").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).goLive(any(), anyString());
    }

    @Test
    void syncNowUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.syncNow(any(), anyString())).thenReturn(store());

        mockMvc.perform(post("/api/app/store/sync-now").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).syncNow(any(), anyString());
    }

    @Test
    void storefrontPreviewUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.storefrontPreview(any())).thenReturn(new ShopifyStorefrontPreviewResponse(
            true,
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "https://bridge.example.com",
            "NOT_ENABLED",
            "LIVE",
            "consumer-1",
            "dep-1",
            "companion-app-embed",
            "Ask the store assistant",
            "Store assistant is ready. Ask about products, policies, or collections.",
            "https://admin.shopify.com/store/alpha/themes/current/editor?context=apps&activateAppId=test-shopify-api-key/companion-app-embed",
            List.of("Catalog product grounding", "Policy grounding"),
            List.of("Judge.me", "Okendo"),
            List.of(
                new ShopifyStorefrontPlacementSummary(
                    "ai-search",
                    "AI search block",
                    "APP_BLOCK",
                    "companion-ai-search",
                    "index",
                    "newAppsSection",
                    "https://admin.shopify.com/store/alpha/themes/current/editor?template=index&addAppBlockId=test-shopify-api-key/companion-ai-search&target=newAppsSection",
                    "FREE",
                    "Use this as the merchant-placeable Free-tier entry point on a homepage or landing template."
                )
            ),
            List.of("Enable the Companion launcher app embed."),
            List.of(),
            "Storefront theme app extension can be enabled now."
        ));

        mockMvc.perform(get("/api/app/store/storefront-preview").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.bridgeBaseUrl").value("https://bridge.example.com"))
            .andExpect(jsonPath("$.surfacePlacements[0].blockHandle").value("companion-ai-search"));

        verify(merchantStoreService).storefrontPreview(any());
    }

    @Test
    void updateWidgetSettingsUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.updateWidgetSettings(any(), any())).thenReturn(store());

        mockMvc.perform(post("/api/app/store/widget-settings")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "launcherLabel": "Need help?",
                      "welcomeMessage": "Ask me about products and policies."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).updateWidgetSettings(any(), any());
    }

    @Test
    void usageSummaryUsesMerchantSessionContext() throws Exception {
        when(usageService.summarize("alpha.myshopify.com")).thenReturn(new ShopifyBridgeUsageSummary(
            "alpha.myshopify.com",
            Instant.parse("2026-04-18T12:00:00Z"),
            Instant.parse("2026-04-18T11:59:00Z"),
            4,
            17,
            List.of(new ShopifyBridgeUsageEventCountSummary("MERCHANT_PLAYGROUND_QUERY", 2)),
            List.of(new ShopifyBridgeUsageEventCountSummary("STOREFRONT_QUERY", 9)),
            List.of(new ShopifyBridgeUsageSurfaceSummary("ai-search", "AI search", 3)),
            List.of(new ShopifyBridgeUsageSurfaceSummary("launcher", "Chat launcher", 9)),
            List.of(new ShopifyBridgeUsageTopQuerySummary(
                "launcher",
                "Chat launcher",
                "What is your return policy?",
                4,
                Instant.parse("2026-04-18T11:59:00Z")
            )),
            List.of(new ShopifyBridgeUsageTopQuerySummary(
                "launcher",
                "Chat launcher",
                "What is your return policy?",
                4,
                Instant.parse("2026-04-18T11:59:00Z")
            )),
            List.of(new ShopifyBridgeUsageTopQuerySummary(
                "launcher",
                "Chat launcher",
                "What is your return policy?",
                4,
                Instant.parse("2026-04-18T11:59:00Z")
            )),
            List.of(),
            new ShopifyBridgeUsageRoiSummary(
                "ACTIONABLE",
                "Companion is producing repeat shopper guidance and decision-support evidence that is strong enough to support launch and commercial rollout claims.",
                9,
                4,
                2,
                1,
                0,
                3,
                List.of("Chat launcher", "Comparison", "AI search"),
                List.of("Use governed-commerce completions as live Elite evidence in rollout packets and merchant value proof points.")
            )
        ));

        mockMvc.perform(get("/api/app/store/usage-summary").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.totalToday").value(4))
            .andExpect(jsonPath("$.last7DayBreakdown[0].eventType").value("STOREFRONT_QUERY"))
            .andExpect(jsonPath("$.last7DaySurfaceUsage[0].surfaceId").value("launcher"))
            .andExpect(jsonPath("$.topQuestionsLast7Days[0].queryText").value("What is your return policy?"))
            .andExpect(jsonPath("$.unansweredQuestionsLast7Days[0].queryText").value("What is your return policy?"))
            .andExpect(jsonPath("$.actionIntentQuestionsLast7Days[0].queryText").value("What is your return policy?"))
            .andExpect(jsonPath("$.last7DaySurfaceJourneys").isArray())
            .andExpect(jsonPath("$.roiSummary.status").value("ACTIONABLE"))
            .andExpect(jsonPath("$.roiSummary.activeSurfaceCount").value(3));

        verify(usageService).summarize("alpha.myshopify.com");
    }

    @Test
    void billingSummaryUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.billingSummary(any())).thenReturn(new ShopifyBridgeBillingSummary(
            "FREE",
            "FREE",
            "Loom Companion Free",
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
                )
            ),
            "The Shopify Companion app is currently running in free mode. No merchant billing approval is required."
        ));

        mockMvc.perform(get("/api/app/store/billing-summary").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("FREE"))
            .andExpect(jsonPath("$.tierKey").value("FREE"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.launchBlocked").value(false))
            .andExpect(jsonPath("$.requiresExplicitConfirmation").value(false))
            .andExpect(jsonPath("$.auditTrailAvailable").value(false))
            .andExpect(jsonPath("$.actionPackages").isArray())
            .andExpect(jsonPath("$.availablePlans[0].requiresExplicitConfirmation").value(false))
            .andExpect(jsonPath("$.availablePlans[0].auditTrailAvailable").value(false))
            .andExpect(jsonPath("$.availablePlans[0].actionPackages").isArray());

        verify(merchantStoreService).billingSummary(any());
    }

    @Test
    void partnerAccessRequestsUseMerchantSessionContext() throws Exception {
        when(merchantStoreService.listPartnerAccessRequests(any())).thenReturn(List.of(
            new ShopifyBridgePartnerAccessRequestSummary(
                "par-1",
                "impl-1",
                "partner-1",
                "Launch Partner",
                "Alpha",
                "merchant@example.com",
                "store-1",
                "alpha.myshopify.com",
                "STARTER",
                List.of("ai-search", "product-faq"),
                List.of("reviews"),
                "Starter launch",
                "IMPLEMENTATION_SUPPORT",
                "WAITING_ON_MERCHANT",
                Instant.parse("2026-04-25T12:00:00Z"),
                Instant.parse("2026-05-25T12:00:00Z"),
                null,
                Instant.parse("2026-04-25T12:00:00Z")
            )
        ));

        mockMvc.perform(get("/api/app/store/partner-access/requests").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].requestId").value("par-1"))
            .andExpect(jsonPath("$[0].partnerName").value("Launch Partner"))
            .andExpect(jsonPath("$[0].status").value("WAITING_ON_MERCHANT"));

        verify(merchantStoreService).listPartnerAccessRequests(any());
    }

    @Test
    void approvePartnerAccessRequestUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.approvePartnerAccessRequest(any(), eq("par-1"), any())).thenReturn(
            new ShopifyBridgePartnerAccessDecisionSummary(
                "par-1",
                "assignment-1",
                "alpha.myshopify.com",
                "ACTIVE",
                Instant.parse("2026-04-25T12:05:00Z")
            )
        );

        mockMvc.perform(post("/api/app/store/partner-access/requests/par-1/approve")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "approvedScope": "IMPLEMENTATION_SUPPORT"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("par-1"))
            .andExpect(jsonPath("$.assignmentId").value("assignment-1"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(merchantStoreService).approvePartnerAccessRequest(any(), eq("par-1"), any());
    }

    @Test
    void denyPartnerAccessRequestUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.denyPartnerAccessRequest(any(), eq("par-1"), any())).thenReturn(
            new ShopifyBridgePartnerAccessDecisionSummary(
                "par-1",
                null,
                "alpha.myshopify.com",
                "DENIED",
                Instant.parse("2026-04-25T12:07:00Z")
            )
        );

        mockMvc.perform(post("/api/app/store/partner-access/requests/par-1/deny")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "decisionReason": "Merchant declined access."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("par-1"))
            .andExpect(jsonPath("$.status").value("DENIED"));

        verify(merchantStoreService).denyPartnerAccessRequest(any(), eq("par-1"), any());
    }

    @Test
    void billingApprovalUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.requestBillingApproval(any(), anyString(), any())).thenReturn(new ShopifyBridgeBillingApprovalResponse(
            "READY_FOR_APPROVAL",
            "https://alpha.myshopify.com/admin/charges/confirm",
            "Redirect the merchant to Shopify to approve the app subscription."
        ));

        mockMvc.perform(post("/api/app/store/billing/approval")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new ShopifyBridgeBillingApprovalRequest("STARTER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("READY_FOR_APPROVAL"))
            .andExpect(jsonPath("$.confirmationUrl").value("https://alpha.myshopify.com/admin/charges/confirm"));

        verify(merchantStoreService).requestBillingApproval(any(), anyString(), any());
    }

    @Test
    void webhookSubscriptionsUseMerchantSessionContext() throws Exception {
        when(merchantStoreService.webhookSubscriptions(any())).thenReturn(new ShopifyWebhookSubscriptionStatusSummary(
            "alpha.myshopify.com",
            "READY",
            "All required Shopify webhook subscriptions are present.",
            "https://bridge.example.com/api/webhooks/shopify",
            9,
            9,
            0,
            0,
            Instant.parse("2026-04-18T12:05:00Z"),
            List.of(new ShopifyWebhookSubscriptionTopicStatusSummary(
                "APP_UNINSTALLED",
                "loom-app-uninstalled",
                "READY",
                "gid://shopify/WebhookSubscription/1",
                "loom-app-uninstalled",
                "https://bridge.example.com/api/webhooks/shopify",
                "Expected subscription is present."
            ))
        ));

        mockMvc.perform(get("/api/app/store/webhook-subscriptions").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.topics[0].topic").value("APP_UNINSTALLED"));

        verify(merchantStoreService).webhookSubscriptions(any());
    }

    @Test
    void playgroundQueryUsesMerchantSessionContext() throws Exception {
        when(merchantPlaygroundService.query(any(), any())).thenReturn(objectMapper.readTree("""
            {
              "conversationId":"conv-1",
              "result":{"sanitizedPayload":{"message":"Here are some backpacks.","suggestions":["Show me policy details"]}}
            }
            """));

        mockMvc.perform(post("/api/app/store/playground/query")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "query":"Show me backpacks"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(merchantPlaygroundService).query(any(), any());
    }

    @Test
    void playgroundSuggestionsUsesMerchantSessionContext() throws Exception {
        when(merchantPlaygroundService.suggestions(any(), any())).thenReturn(objectMapper.readTree("""
            {
              "suggestions":["Show me best sellers","What is your shipping policy?"]
            }
            """));

        mockMvc.perform(post("/api/app/store/playground/suggestions")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "content":"bags"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestions[0]").value("Show me best sellers"));

        verify(merchantPlaygroundService).suggestions(any(), any());
    }

    @Test
    void updateSourceSettingsUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.updateSourceSettings(any(), any())).thenReturn(store());

        mockMvc.perform(post("/api/app/store/source-settings")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "productsEnabled": true,
                      "collectionsEnabled": false,
                      "pagesEnabled": true,
                      "policiesEnabled": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).updateSourceSettings(any(), any());
    }

    @Test
    void vectorizationSummaryUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.vectorization(any())).thenReturn(vectorization());

        mockMvc.perform(get("/api/app/store/vectorization").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.policy.policyVersion").value(1))
            .andExpect(jsonPath("$.effectiveIndexedFields[0].fieldKey").value("products.title"));

        verify(merchantStoreService).vectorization(any());
    }

    @Test
    void vectorizationPolicyUpdateUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.updateVectorizationPolicy(any(), any())).thenReturn(vectorization());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/app/store/vectorization/policy")
                .header("Authorization", "Bearer " + token())
                .contentType("application/json")
                .content("""
                    {
                      "policyVersion": 1,
                      "sourcePolicies": [
                        {
                          "sourceCategory": "products",
                          "autoIndexingEnabled": true,
                          "createTriggerEnabled": true,
                          "deleteTriggerEnabled": true,
                          "updateTriggerMode": "INDEXED_FIELDS_ONLY",
                          "selectedIndexedFields": ["products.title"],
                          "debounceWindowSeconds": 30,
                          "minimumRunIntervalSeconds": 60
                        }
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.policy.policyVersion").value(1));

        verify(merchantStoreService).updateVectorizationPolicy(any(), any());
    }

    private ShopifyBridgeStoreSummary store() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
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
            "CONNECTED",
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
            new ShopifyBridgeStoreWidgetSummary(
                "NOT_ENABLED",
                Instant.parse("2026-04-18T00:00:00Z"),
                "THEME_APP_EXTENSION",
                "Theme app extension not enabled yet.",
                new ShopifyBridgeStoreWidgetSettingsSummary(
                    "Ask the store assistant",
                    "Store assistant is ready. Ask about products, policies, or collections.",
                    "SHOPIFY_COMPANION",
                    List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison"),
                    "navigator",
                    List.of("navigator", "executor"),
                    java.util.Map.of("account", "executor")
                )
            ),
            new ShopifyBridgeStoreCapabilitySummary(
                2,
                2,
                2,
                1,
                List.of("list_products", "get_policy"),
                List.of("shopify-catalog", "shopify-policies"),
                List.of("search", "support"),
                List.of("shopify-products")
            ),
            new ShopifyBridgeStoreReadinessSummary(
                "BLOCKED",
                false,
                false,
                List.of("Shopify source readiness is not READY yet."),
                List.of("Shopify source readiness is not READY yet."),
                List.of("Run source preflight and resolve any blocked Shopify source categories.")
            ),
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }

    private ShopifyBridgeStoreVectorizationSummary vectorization() {
        return new ShopifyBridgeStoreVectorizationSummary(
            "alpha.myshopify.com",
            "dep-1",
            true,
            List.of("products"),
            List.of("product"),
            List.of("mkp-data-shopify-catalog"),
            List.of("mkp-data-shopify-catalog"),
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
            "APPLIED_VERIFIED",
            "PLATFORM_MANAGED_AUTO",
            "IN_SYNC",
            true,
            List.of(),
            null,
            new ShopifyBridgeStoreVectorizationPolicySummary(
                1,
                false,
                List.of(new ShopifyBridgeStoreVectorizationSourcePolicySummary(
                    "products",
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    "INDEXED_FIELDS_ONLY",
                    List.of(),
                    30,
                    60
                )),
                "system",
                Instant.parse("2026-04-18T12:00:00Z")
            ),
            List.of(new ShopifyBridgeStoreVectorizationIndexedFieldSummary(
                "products.title",
                "products",
                "product",
                "title",
                "Product title",
                true
            )),
            new ShopifyBridgeStoreVectorizationAutomationSummary(
                true,
                0,
                0,
                0,
                0,
                0,
                0,
                Instant.parse("2026-04-18T12:00:00Z"),
                Instant.parse("2026-04-18T12:00:00Z"),
                null,
                "vrn-1",
                List.of()
            ),
            List.of(new ShopifyBridgeStoreVectorizationEventSummary(
                "evt-1",
                "products",
                "product",
                "gid://shopify/Product/1",
                "products/update",
                "UPDATE",
                "COMPLETED",
                "AUTO_INDEX",
                null,
                "vrn-1",
                "wh-1",
                Instant.parse("2026-04-18T12:00:00Z"),
                Instant.parse("2026-04-18T12:00:01Z"),
                Instant.parse("2026-04-18T12:00:02Z"),
                Instant.parse("2026-04-18T12:00:03Z"),
                "Completed"
            ))
        );
    }

    private String token() {
        try {
            String header = base64Url("""
                {"alg":"HS256","typ":"JWT"}
                """.trim());
            long now = Instant.now().getEpochSecond();
            String payload = base64Url("""
                {"iss":"https://alpha.myshopify.com/admin","dest":"https://alpha.myshopify.com","aud":"test-shopify-api-key","sub":"gid://shopify/User/1","nbf":%d,"exp":%d}
                """.formatted(now - 10, now + 120));
            String signingInput = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-shopify-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
