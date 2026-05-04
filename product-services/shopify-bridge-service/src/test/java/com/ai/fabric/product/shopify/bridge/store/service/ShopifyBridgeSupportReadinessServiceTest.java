package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeStoreBillingState;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.mcp.execution.McpActionExecutionGateway;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordedBillingStateSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportProfileSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeSupportReadinessServiceTest {

    @Test
    void summarizesReadyOrderLookupWhenScopeAndWebhookArePresent() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);

        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            "support@alpha.test",
            "https://alpha.test/contact",
            "https://alpha.test/help",
            "/pages/order-help",
            "Refund approvals still route to the merchant support team.",
            true
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord(
            "read_products,read_content,read_legal_policies,read_orders",
            true
        )));
        when(billingService.summarize()).thenReturn(eliteBillingSummary());

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.orderLookupSupported()).isTrue();
        assertThat(summary.orderLookupScopeGranted()).isTrue();
        assertThat(summary.appScopesUpdateWebhookReady()).isTrue();
        assertThat(summary.lifecycleStage()).isEqualTo("RECENT_ORDER_ONLY");
        assertThat(summary.activeSubscriptionNames()).isEmpty();
        assertThat(summary.activeSubscriptions()).isEmpty();
        assertThat(summary.merchantHandoffConfigured()).isTrue();
        assertThat(summary.supportProfile()).isNotNull();
        assertThat(summary.supportProfile().contactEmail()).isEqualTo("support@alpha.test");
        assertThat(summary.supportedCapabilities()).contains("order-status", "tracking-link");
    }

    @Test
    void reportsPendingScopeGrantWhenReadOrdersIsMissing() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);

        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            null,
            null,
            null,
            null,
            null,
            false
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord(
            "read_products,read_content,read_legal_policies",
            false
        )));
        when(billingService.summarize()).thenReturn(eliteBillingSummary());

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("PENDING_SCOPE_GRANT");
        assertThat(summary.orderLookupSupported()).isFalse();
        assertThat(summary.lifecycleStage()).isEqualTo("SCOPE_APPROVAL");
        assertThat(summary.merchantHandoffConfigured()).isFalse();
        assertThat(summary.missingScopes()).containsExactly("read_orders");
        assertThat(summary.message()).contains("order-read scope approval");
        assertThat(summary.nextActions()).anyMatch(action -> action.contains("read_orders"));
    }

    @Test
    void degradesReadySupportPostureWhenMcpToolsDrift() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);
        McpActionExecutionGateway mcpActionExecutionGateway = mock(McpActionExecutionGateway.class);

        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            "support@alpha.test",
            "https://alpha.test/contact",
            null,
            null,
            null,
            true
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord(
            "read_products,read_content,read_legal_policies",
            true
        )));
        when(billingService.summarize()).thenReturn(billingSummary());
        when(mcpActionExecutionGateway.storefrontReadiness("alpha.myshopify.com")).thenReturn(Map.of(
            "ready",
            false,
            "servers",
            List.of(Map.of(
                "serverRef",
                "shopify-storefront-ucp",
                "ready",
                false,
                "expectedTools",
                List.of("search_catalog", "lookup_catalog", "get_product"),
                "presentTools",
                List.of("lookup_catalog"),
                "missingTools",
                List.of("search_catalog", "get_product")
            ))
        ));

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties(),
            mcpActionExecutionGateway
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("DEGRADED");
        assertThat(summary.lifecycleStage()).isEqualTo("MCP_DRIFT");
        assertThat(summary.message()).contains("MCP endpoint/tool readiness");
        assertThat(summary.verificationMethods()).contains("SHOPIFY_MCP_TOOLS_LIST");
        assertThat(summary.supportedCapabilities()).contains("mcp:shopify-storefront-ucp:lookup_catalog");
        assertThat(summary.blockedCapabilities())
            .contains("mcp:shopify-storefront-ucp:search_catalog", "mcp:shopify-storefront-ucp:get_product");
        assertThat(summary.nextActions()).anyMatch(action -> action.contains("missing tools: search_catalog, get_product"));
    }

    @Test
    void usesPlatformBillingStateBeforeStaleLocalInstallBilling() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);

        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            "support@alpha.test",
            "https://alpha.test/contact",
            null,
            null,
            null,
            true
        ));
        when(platformClient.getBillingState("alpha.myshopify.com")).thenReturn(new ShopifyBridgeRecordedBillingStateSummary(
            "alpha.myshopify.com",
            "ELITE",
            "ACTIVE",
            null,
            "Loom Companion Elite",
            Instant.parse("2026-04-23T12:00:00Z"),
            "Live verification activation"
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord(
            "read_products,read_content,read_legal_policies",
            true,
            "FREE",
            "ACTIVE",
            List.of()
        )));

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.billingTier()).isEqualTo("ELITE");
        assertThat(summary.billingStatus()).isEqualTo("ACTIVE");
        assertThat(summary.status()).isEqualTo("PENDING_SCOPE_GRANT");
        assertThat(summary.lifecycleStage()).isEqualTo("SCOPE_APPROVAL");
        assertThat(summary.scopeGrantRequired()).isTrue();
        assertThat(summary.orderLookupSupported()).isFalse();
        assertThat(summary.missingScopes()).containsExactly("read_orders");
    }

    @Test
    void reconcilesWebhookReadinessFromLiveCredentialsWhenPersistedInstallRecordIsStale() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);

        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            null,
            "https://alpha.test/contact",
            null,
            null,
            null,
            true
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord(
            "read_products,read_content,read_legal_policies",
            false
        )));
        when(platformClient.resolveCredentialMaterial("alpha.myshopify.com")).thenReturn(new ShopifyBridgeResolvedStoreCredentials(
            "access-token",
            null,
            null,
            null,
            "read_products,read_content,read_legal_policies",
            false
        ));
        when(webhookSubscriptionService.inspectTopicStatus("alpha.myshopify.com", "access-token", "APP_SCOPES_UPDATE"))
            .thenReturn(new ShopifyWebhookSubscriptionTopicStatusSummary(
                "APP_SCOPES_UPDATE",
                "loom-app-scopes-update",
                "READY",
                "gid://shopify/WebhookSubscription/1",
                "loom-app-scopes-update",
                "https://bridge.example.com/api/webhooks/shopify",
                "Expected subscription is present."
            ));
        when(billingService.summarize()).thenReturn(billingSummary());
        when(billingService.inspectStoreBillingState("alpha.myshopify.com", "access-token"))
            .thenReturn(new ShopifyBridgeStoreBillingState("FREE", "ACTIVE", List.of()));

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.orderLookupScopeGranted()).isFalse();
        assertThat(summary.appScopesUpdateWebhookReady()).isTrue();
        assertThat(summary.lifecycleStage()).isEqualTo("MERCHANT_HANDOFF");
        verify(installRecordService).recordAppScopesUpdateWebhookReady("alpha.myshopify.com", true);
    }

    @Test
    void fallsBackToPersistedPlatformCredentialScopesWhenInstallRecordIsMissing() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);

        ShopifyInstallRecordSummary recovered = installRecord(
            "read_products,read_orders",
            false,
            "STARTER",
            "ACTIVE",
            List.of(new ShopifyBridgeSupportSubscriptionSummary(
                "gid://shopify/AppSubscription/1",
                "Loom Companion Starter",
                "ACTIVE",
                "STARTER",
                true
            ))
        );
        ShopifyInstallRecordSummary recoveredWithWebhook = installRecord(
            "read_products,read_orders",
            true,
            "STARTER",
            "ACTIVE",
            recovered.activeSubscriptions()
        );
        when(installRecordService.findByShopDomain("alpha.myshopify.com"))
            .thenReturn(Optional.empty(), Optional.of(recoveredWithWebhook));
        when(platformClient.resolveCredentialMaterial("alpha.myshopify.com")).thenReturn(new ShopifyBridgeResolvedStoreCredentials(
            "access-token",
            null,
            null,
            null,
            "read_products,read_orders",
            false
        ));
        when(webhookSubscriptionService.inspectTopicStatus("alpha.myshopify.com", "access-token", "APP_SCOPES_UPDATE"))
            .thenReturn(new ShopifyWebhookSubscriptionTopicStatusSummary(
                "APP_SCOPES_UPDATE",
                "loom-app-scopes-update",
                "READY",
                "gid://shopify/WebhookSubscription/1",
                "loom-app-scopes-update",
                "https://bridge.example.com/api/webhooks/shopify",
                "Expected subscription is present."
            ));
        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            "support@alpha.test",
            null,
            null,
            null,
            null,
            true
        ));
        when(billingService.inspectStoreBillingState("alpha.myshopify.com", "access-token"))
            .thenReturn(new ShopifyBridgeStoreBillingState(
                "STARTER",
                "ACTIVE",
                recovered.activeSubscriptions()
            ));

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.installStatus()).isEqualTo("INSTALLED");
        assertThat(summary.billingTier()).isEqualTo("STARTER");
        assertThat(summary.orderLookupScopeGranted()).isTrue();
        assertThat(summary.orderLookupSupported()).isFalse();
        assertThat(summary.appScopesUpdateWebhookReady()).isTrue();
        assertThat(summary.activeSubscriptionNames()).containsExactly("Loom Companion Starter");
    }

    @Test
    void keepsMerchantHandoffBlockedWhenPlatformSupportProfileIsBlank() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);

        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            null,
            null,
            null,
            null,
            null,
            false
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord(
            "read_products,read_content,read_legal_policies,read_orders",
            true
        )));
        when(billingService.summarize()).thenReturn(billingSummary());

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.merchantHandoffConfigured()).isFalse();
        assertThat(summary.supportProfile().contactEmail()).isNull();
        assertThat(summary.lifecycleStage()).isEqualTo("SUPPORT_HANDOFF_SETUP");
        assertThat(summary.nextActions()).anyMatch(action -> action.contains("merchant support email"));
    }

    private ShopifyBridgeStoreSummary store(String installStatus) {
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
            "ACTIVE",
            "consumer-1",
            "Alpha Storefront",
            installStatus,
            "SYNCED",
            "READY",
            "ENABLED",
            "LIVE",
            true,
            true,
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
            new ShopifyBridgeStoreReadinessSummary(
                "READY",
                true,
                true,
                List.of(),
                List.of(),
                List.of()
            ),
            null,
            null,
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z")
        );
    }

    private ShopifyInstallRecordSummary installRecord(String scopesText,
                                                      boolean appScopesUpdateWebhookReady,
                                                      String billingTierKey,
                                                      String billingStatus,
                                                      List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions) {
        Instant now = Instant.parse("2026-04-23T12:00:00Z");
        return new ShopifyInstallRecordSummary(
            "alpha.myshopify.com",
            "INSTALLED",
            "https://alpha.myshopify.com",
            "merchant-user",
            "embedded-host",
            "secret://access",
            "secret://refresh",
            scopesText,
            now.plusSeconds(3600),
            now.plusSeconds(7200),
            appScopesUpdateWebhookReady,
            now,
            billingTierKey,
            billingStatus,
            activeSubscriptions,
            now,
            now,
            now,
            null
        );
    }

    private ShopifyInstallRecordSummary installRecord(String scopesText, boolean appScopesUpdateWebhookReady) {
        return installRecord(scopesText, appScopesUpdateWebhookReady, null, null, List.of());
    }

    private ShopifyBridgeBillingSummary billingSummary() {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "FREE",
            "Loom Companion Free",
            "ACTIVE",
            false,
            false,
            false,
            false,
            50,
            "MANUAL",
            true,
            false,
            false,
            false,
            List.of(),
            List.of("ai-search"),
            List.of(),
            "Billing ready."
        );
    }

    private ShopifyBridgeBillingSummary eliteBillingSummary() {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
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
            List.of("guided-commerce"),
            List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison", "order-lookup"),
            List.of(),
            "Elite tier is active."
        );
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Loom Companion",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "production",
            "https://bridge.example",
            "2026-04",
            "api-key",
            "api-secret",
            "https://platform.example",
            "platform-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-key",
            "X-BRIDGE-API-KEY"
        );
    }
}
