package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSettingsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontReadActionRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontReadActionServiceTest {

    @Test
    void executesShopperSafeComparisonActionWhenSurfaceIsEnabled() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeActionExecutionService actionExecutionService = mock(ShopifyBridgeActionExecutionService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(List.of("comparison", "product-insight"), true));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", null, List.of("comparison", "product-insight")))
            .thenReturn(List.of("comparison", "product-insight"));
        when(actionExecutionService.execute(eq("alpha.myshopify.com"), any())).thenReturn(
            ShopifyBridgeActionResult.ok("Similar products", Map.of("count", 2))
        );

        ShopifyStorefrontReadActionService service = new ShopifyStorefrontReadActionService(
            platformClient,
            installCredentialService,
            billingService,
            actionExecutionService,
            usageService
        );

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyStorefrontReadActionRequest(
                "find_similar_products",
                Map.of("sku", "SKU-1", "limit", 12),
                "comparison"
            ),
            "shopper-session-1"
        );

        assertThat(result.success()).isTrue();
        verify(actionExecutionService).execute(eq("alpha.myshopify.com"), any());
        verify(usageService).recordEvent("alpha.myshopify.com", "STOREFRONT_READ_ACTION_COMPARISON");
    }

    @Test
    void executesComparisonActionWhenSupportGateIsTheOnlyBlocker() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeActionExecutionService actionExecutionService = mock(ShopifyBridgeActionExecutionService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(List.of("comparison"), false));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", null, List.of("comparison")))
            .thenReturn(List.of("comparison"));
        when(actionExecutionService.execute(eq("alpha.myshopify.com"), any())).thenReturn(
            ShopifyBridgeActionResult.ok("Compared", Map.of("count", 2))
        );

        ShopifyStorefrontReadActionService service = new ShopifyStorefrontReadActionService(
            platformClient,
            installCredentialService,
            billingService,
            actionExecutionService,
            usageService
        );

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyStorefrontReadActionRequest(
                "find_similar_products",
                Map.of("sku", "SKU-1"),
                "comparison"
            ),
            "shopper-session-1"
        );

        assertThat(result.success()).isTrue();
        verify(actionExecutionService).execute(eq("alpha.myshopify.com"), any());
    }

    @Test
    void blocksComparisonSurfaceWhenTierDoesNotAllowIt() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeActionExecutionService actionExecutionService = mock(ShopifyBridgeActionExecutionService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(List.of("comparison"), true));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", null, List.of("comparison")))
            .thenReturn(List.of("ai-search"));

        ShopifyStorefrontReadActionService service = new ShopifyStorefrontReadActionService(
            platformClient,
            installCredentialService,
            billingService,
            actionExecutionService,
            usageService
        );

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyStorefrontReadActionRequest(
                "find_similar_products",
                Map.of("sku", "SKU-1"),
                "comparison"
            ),
            "shopper-session-1"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SURFACE_NOT_ENABLED");
    }

    @Test
    void rejectsUnsafeComparisonRequestShape() {
        ShopifyStorefrontReadActionService service = new ShopifyStorefrontReadActionService(
            mock(PlatformShopifyStoreClient.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyBridgeActionExecutionService.class),
            mock(ShopifyBridgeUsageService.class)
        );

        ShopifyBridgeActionResult result = service.execute(
            "alpha.myshopify.com",
            new ShopifyStorefrontReadActionRequest(
                "compare_products",
                Map.of("referenceSku", "SKU-1", "comparisonSku", "SKU-1"),
                "comparison"
            ),
            "shopper-session-1"
        );

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_REQUEST");
    }

    @SuppressWarnings("SameParameterValue")
    private ShopifyBridgeStoreSummary store(List<String> enabledSurfaces, boolean storefrontReady) {
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
            "INSTALLED",
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
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA",
                Instant.parse("2026-04-23T12:00:00Z"),
                Instant.parse("2026-04-23T12:00:00Z"),
                Instant.parse("2026-07-23T12:00:00Z"),
                "read_products,read_content,read_legal_policies",
                false
            ),
            null,
            null,
            null,
            new ShopifyBridgeStoreWidgetSummary(
                "ENABLED",
                Instant.parse("2026-04-23T12:00:00Z"),
                "THEME_APP_EXTENSION",
                "Widget ready.",
                new ShopifyBridgeStoreWidgetSettingsSummary(
                    "Need help?",
                    "Ask me about products and policies.",
                    "SHOPIFY_COMPANION",
                    enabledSurfaces
                )
            ),
            null,
            new ShopifyBridgeStoreReadinessSummary(
                storefrontReady ? "READY" : "BLOCKED",
                storefrontReady,
                storefrontReady,
                storefrontReady ? List.of() : List.of("Install the theme app embed."),
                storefrontReady ? List.of() : List.of("Install the theme app embed."),
                storefrontReady ? List.of() : List.of("Activate the storefront widget.")
            ),
            null,
            new ShopifyBridgeStoreDeploymentReleaseSummary(
                "rel-1",
                "ver-1",
                "APPLIED_VERIFIED",
                "PASSED",
                "SUCCEEDED",
                "completed",
                "Release applied and verified.",
                null,
                Instant.parse("2026-04-23T12:00:00Z"),
                Instant.parse("2026-04-23T12:00:00Z"),
                Instant.parse("2026-04-23T12:00:00Z")
            ),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z"),
            Instant.parse("2026-04-23T12:00:00Z")
        );
    }

    @SuppressWarnings("unused")
    private ShopifyBridgeBillingSummary starterSummary() {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "STARTER",
            "Loom Companion Starter",
            "ACTIVE",
            false,
            true,
            false,
            false,
            null,
            "EVERY_2_HOURS",
            false,
            false,
            false,
            false,
            List.of(),
            List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison"),
            List.of(
                new ShopifyBridgeBillingPlanSummary(
                    "STARTER",
                    "Loom Companion Starter",
                    "49.00",
                    "USD",
                    "EVERY_30_DAYS",
                    false,
                    true,
                    false,
                    false,
                    null,
                    "EVERY_2_HOURS",
                    false,
                    false,
                    false,
                    false,
                    List.of(),
                    List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison"),
                    "Starter tier is active."
                )
            ),
            "Starter tier is active."
        );
    }
}
