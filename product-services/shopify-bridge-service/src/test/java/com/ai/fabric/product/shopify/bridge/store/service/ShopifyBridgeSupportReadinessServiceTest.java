package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportProfileSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionDiagnosticsService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyBridgeSupportReadinessServiceTest {

    @Test
    void summarizesReadyOrderLookupWhenScopeAndWebhookArePresent() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionDiagnosticsService webhookDiagnosticsService = mock(ShopifyWebhookSubscriptionDiagnosticsService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED"));
        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            "support@alpha.test",
            "https://alpha.test/contact",
            "https://alpha.test/help",
            "/pages/order-help",
            "Refund approvals still route to the merchant support team.",
            true
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord("read_products,read_content,read_legal_policies,read_orders")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies,read_orders")));
        when(billingService.summarizeForShop("alpha.myshopify.com", "access-token")).thenReturn(billingSummary());
        when(webhookDiagnosticsService.forShop("alpha.myshopify.com")).thenReturn(readyWebhookSummary());
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), anyString())).thenReturn(Map.of(
            "data", Map.of(
                "currentAppInstallation", Map.of(
                    "accessScopes", List.of(
                        Map.of("handle", "read_products"),
                        Map.of("handle", "read_orders")
                    ),
                    "activeSubscriptions", List.of(
                        Map.of("name", "Loom Companion Free", "status", "ACTIVE")
                    )
                )
            )
        ));

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            webhookDiagnosticsService,
            shopifyAdminGraphqlClient,
            properties()
        );

        var summary = service.summarizeForShop("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.orderLookupSupported()).isTrue();
        assertThat(summary.orderLookupScopeGranted()).isTrue();
        assertThat(summary.appScopesUpdateWebhookReady()).isTrue();
        assertThat(summary.lifecycleStage()).isEqualTo("RECENT_ORDER_ONLY");
        assertThat(summary.activeSubscriptionNames()).contains("Loom Companion Free");
        assertThat(summary.activeSubscriptions()).hasSize(1);
        assertThat(summary.merchantHandoffConfigured()).isTrue();
        assertThat(summary.supportProfile()).isNotNull();
        assertThat(summary.supportProfile().contactEmail()).isEqualTo("support@alpha.test");
        assertThat(summary.supportedCapabilities()).contains("order-status", "tracking-link");
    }

    @Test
    void reportsPendingScopeGrantWhenReadOrdersIsMissing() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyWebhookSubscriptionDiagnosticsService webhookDiagnosticsService = mock(ShopifyWebhookSubscriptionDiagnosticsService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED"));
        when(platformClient.getSupportProfile("alpha.myshopify.com")).thenReturn(new ShopifyBridgeSupportProfileSummary(
            null,
            null,
            null,
            null,
            null,
            false
        ));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord("read_products,read_content,read_legal_policies")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies")));
        when(billingService.summarizeForShop("alpha.myshopify.com", "access-token")).thenReturn(billingSummary());
        when(webhookDiagnosticsService.forShop("alpha.myshopify.com")).thenReturn(readyWebhookSummary());
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), anyString())).thenReturn(Map.of(
            "data", Map.of(
                "currentAppInstallation", Map.of(
                    "accessScopes", List.of(Map.of("handle", "read_products")),
                    "activeSubscriptions", List.of()
                )
            )
        ));

        ShopifyBridgeSupportReadinessService service = new ShopifyBridgeSupportReadinessService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            webhookDiagnosticsService,
            shopifyAdminGraphqlClient,
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

    private ShopifyInstallRecordSummary installRecord(String scopesText) {
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
            now,
            now,
            null
        );
    }

    private ShopifyBridgeCredentialAcquisition acquisition(String scopesText) {
        return new ShopifyBridgeCredentialAcquisition(
            null,
            new ShopifyTokenExchangeMaterial(
                "access-token",
                "refresh-token",
                Instant.parse("2026-04-23T13:00:00Z"),
                Instant.parse("2026-04-24T12:00:00Z"),
                scopesText,
                false
            )
        );
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
            List.of("ai-search", "order-lookup"),
            List.of(),
            "Billing ready."
        );
    }

    private ShopifyWebhookSubscriptionStatusSummary readyWebhookSummary() {
        return new ShopifyWebhookSubscriptionStatusSummary(
            "alpha.myshopify.com",
            "READY",
            "Webhook subscriptions are ready.",
            "https://bridge.example/api/webhooks/shopify",
            10,
            10,
            0,
            0,
            Instant.parse("2026-04-23T12:00:00Z"),
            List.of(
                new ShopifyWebhookSubscriptionTopicStatusSummary(
                    "APP_SCOPES_UPDATE",
                    "loom-app-scopes-update",
                    "READY",
                    "gid://shopify/WebhookSubscription/1",
                    "loom-app-scopes-update",
                    "https://bridge.example/api/webhooks/shopify",
                    "Ready"
                )
            )
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
