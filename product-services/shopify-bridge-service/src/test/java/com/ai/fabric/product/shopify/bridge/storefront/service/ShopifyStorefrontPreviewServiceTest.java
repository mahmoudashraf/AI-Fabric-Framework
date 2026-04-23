package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSettingsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStorefrontPreviewServiceTest {

    @Test
    void previewReturnsActivationGuidanceWhenStorefrontIsReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(true));

        ShopifyStorefrontPreviewService service = new ShopifyStorefrontPreviewService(
            platformClient,
            new ShopifyBridgeProperties(
                "Shopify Bridge",
                "shopify-bridge-prod",
                "SHOPIFY",
                "SHOPIFY_BRIDGE_SERVICE",
                "prod",
                "https://bridge.example.com",
                "2026-04",
                "shopify-api-key",
                "shopify-api-secret",
                "https://platform.example.com",
                "platform-admin-key",
                "X-PLATFORM-API-KEY",
                "webhook-secret",
                "bridge-admin-key",
                "X-BRIDGE-API-KEY"
            )
        );

        ShopifyStorefrontPreviewResponse preview = service.preview("alpha.myshopify.com");

        assertThat(preview.ready()).isTrue();
        assertThat(preview.bridgeBaseUrl()).isEqualTo("https://bridge.example.com");
        assertThat(preview.launcherLabelDefault()).isEqualTo("Need help?");
        assertThat(preview.welcomeMessageDefault()).isEqualTo("Ask me about products and store policies.");
        assertThat(preview.themeEditorActivationUrl())
            .isEqualTo("https://admin.shopify.com/store/alpha/themes/current/editor?context=apps&activateAppId=shopify-api-key/companion-app-embed");
        assertThat(preview.surfacePlacements()).hasSize(6);
        assertThat(preview.surfacePlacements().get(0).blockHandle()).isEqualTo("companion-ai-search");
        assertThat(preview.surfacePlacements().get(0).requiredTierKey()).isEqualTo("FREE");
        assertThat(preview.surfacePlacements().get(1).themeEditorUrl())
            .contains("addAppBlockId=shopify-api-key/companion-contextual-pill");
        assertThat(preview.surfacePlacements().get(4).blockHandle()).isEqualTo("companion-product-faq");
        assertThat(preview.surfacePlacements().get(5).blockHandle()).isEqualTo("companion-comparison");
        assertThat(preview.activationSteps()).isNotEmpty();
    }

    @Test
    void previewReportsBlockersWhenStorefrontIsNotReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(false));

        ShopifyStorefrontPreviewService service = new ShopifyStorefrontPreviewService(
            platformClient,
            new ShopifyBridgeProperties(
                "Shopify Bridge",
                "shopify-bridge-prod",
                "SHOPIFY",
                "SHOPIFY_BRIDGE_SERVICE",
                "prod",
                "",
                "2026-04",
                "shopify-api-key",
                "shopify-api-secret",
                "https://platform.example.com",
                "platform-admin-key",
                "X-PLATFORM-API-KEY",
                "webhook-secret",
                "bridge-admin-key",
                "X-BRIDGE-API-KEY"
            )
        );

        ShopifyStorefrontPreviewResponse preview = service.preview("alpha.myshopify.com");

        assertThat(preview.ready()).isFalse();
        assertThat(preview.blockingReasons()).isNotEmpty();
        assertThat(preview.message()).contains("Store data is not ready yet");
    }

    private ShopifyBridgeStoreSummary store(boolean ready) {
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
            "APPLIED_VERIFIED",
            "consumer-alpha",
            "Alpha Storefront",
            "INSTALLED",
            ready ? "SYNCED" : "NOT_SYNCED",
            ready ? "READY" : "NOT_RUN",
            ready ? "ENABLED" : "NOT_ENABLED",
            ready ? "LIVE" : "BLOCKED",
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
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            ),
            null,
            null,
            null,
            new ShopifyBridgeStoreWidgetSummary(
                ready ? "ENABLED" : "NOT_ENABLED",
                Instant.parse("2026-04-18T00:00:00Z"),
                "THEME_APP_EXTENSION",
                ready ? "Theme extension loaded successfully." : "Theme extension not enabled yet.",
                new ShopifyBridgeStoreWidgetSettingsSummary(
                    "Need help?",
                    "Ask me about products and store policies.",
                    "GUIDED_COMMERCE",
                    List.of("ai-search", "comparison")
                )
            ),
            null,
            ready
                ? new ShopifyBridgeStoreReadinessSummary("STOREFRONT_READY", true, true, List.of(), List.of(), List.of())
                : new ShopifyBridgeStoreReadinessSummary(
                    "BLOCKED",
                    false,
                    false,
                    List.of("Shopify source readiness is not READY yet."),
                    List.of("Store data is not ready yet. Run source preflight and complete publish/apply/verify before enabling the widget."),
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
}
