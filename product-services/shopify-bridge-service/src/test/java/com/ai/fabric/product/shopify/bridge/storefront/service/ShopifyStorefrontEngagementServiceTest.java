package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentVersionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontEngagementEventRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontEngagementServiceTest {

    @Test
    void recordsWidgetOpenedWithPageContext() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyStorefrontEngagementService service = new ShopifyStorefrontEngagementService(platformClient, usageService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(readyStore());

        service.record(
            "alpha.myshopify.com",
            new ShopifyStorefrontEngagementEventRequest("WIDGET_OPENED", "product", "Travel Pack", "travel-pack", null),
            "shopper-session-1"
        );

        verify(usageService).recordEvent("alpha.myshopify.com", "STOREFRONT_WIDGET_OPENED_PRODUCT_PAGE");
    }

    @Test
    void rejectsUnsupportedEventType() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyStorefrontEngagementService service = new ShopifyStorefrontEngagementService(platformClient, usageService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(readyStore());

        assertThatThrownBy(() -> service.record(
            "alpha.myshopify.com",
            new ShopifyStorefrontEngagementEventRequest("UNSUPPORTED", "product", null, null, null),
            "shopper-session-1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unsupported storefront event type");
    }

    @Test
    void rejectsEventsWhenStorefrontIsNotReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyStorefrontEngagementService service = new ShopifyStorefrontEngagementService(platformClient, usageService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(blockedStore());

        assertThatThrownBy(() -> service.record(
            "alpha.myshopify.com",
            new ShopifyStorefrontEngagementEventRequest("WIDGET_OPENED", "product", null, null, null),
            "shopper-session-1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Store data is not ready yet");
    }

    private ShopifyBridgeStoreSummary readyStore() {
        return store(readiness(true, java.util.List.of()));
    }

    private ShopifyBridgeStoreSummary blockedStore() {
        return store(readiness(false, java.util.List.of("Store data is not ready yet. Run source preflight and complete publish/apply/verify before enabling the widget.")));
    }

    private ShopifyBridgeStoreReadinessSummary readiness(boolean storefrontReady, java.util.List<String> storefrontBlockingReasons) {
        return new ShopifyBridgeStoreReadinessSummary(
            storefrontReady ? "STOREFRONT_READY" : "BLOCKED",
            storefrontReady,
            storefrontReady,
            storefrontReady ? java.util.List.of() : java.util.List.of("Shopify source readiness is not READY yet."),
            storefrontBlockingReasons,
            storefrontReady ? java.util.List.of() : java.util.List.of("Run source preflight and resolve any blocked Shopify source categories.")
        );
    }

    private ShopifyBridgeStoreSummary store(ShopifyBridgeStoreReadinessSummary readiness) {
        Instant now = Instant.parse("2026-04-18T00:00:00Z");
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
            "SYNCED",
            "READY",
            "ENABLED",
            "PREFLIGHT_READY",
            true,
            true,
            true,
            true,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                now,
                now,
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            ),
            null,
            null,
            null,
            new ShopifyBridgeStoreWidgetSummary(
                "ENABLED",
                now,
                "THEME_APP_EXTENSION",
                "Theme extension status recorded.",
                null
            ),
            null,
            readiness,
            new ShopifyBridgeStoreDeploymentVersionSummary("ver-1", "v1", "PUBLISHED", now),
            new ShopifyBridgeStoreDeploymentReleaseSummary(
                "rel-1",
                "ver-1",
                "APPLIED_VERIFIED",
                "PASSED",
                "SUCCEEDED",
                "completed",
                "Release applied and verified.",
                null,
                now,
                now,
                now
            ),
            now,
            now,
            now,
            now,
            now
        );
    }
}
