package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSettingsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupRequest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontOrderLookupServiceTest {

    @Test
    void returnsMatchedOrderSummaryWhenVerifiedOrderExists() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(true, List.of("ai-search", "order-lookup")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies,read_orders")));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord("read_products,read_content,read_legal_policies,read_orders")));
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", "access-token", List.of("ai-search", "order-lookup")))
            .thenReturn(List.of("ai-search", "order-lookup"));
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), anyString(), anyMap())).thenReturn(Map.of(
            "data", Map.of(
                "orders", Map.of(
                    "nodes", List.of(
                        Map.of(
                            "name", "#1001",
                            "confirmationNumber", "CN-1001",
                            "createdAt", "2026-04-23T12:00:00Z",
                            "displayFinancialStatus", "PAID",
                            "displayFulfillmentStatus", "FULFILLED",
                            "statusPageUrl", "https://alpha.myshopify.com/orders/status/1001",
                            "currentTotalPriceSet", Map.of(
                                "shopMoney", Map.of("amount", "59.00", "currencyCode", "USD")
                            ),
                            "lineItems", Map.of(
                                "nodes", List.of(
                                    Map.of("title", "Alpha Hoodie", "variantTitle", "Medium", "quantity", 1)
                                )
                            ),
                            "fulfillments", List.of(
                                Map.of(
                                    "trackingInfo", List.of(
                                        Map.of("company", "UPS", "number", "1Z999999", "url", "https://tracking.example/1")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        ));

        ShopifyStorefrontOrderLookupService service = new ShopifyStorefrontOrderLookupService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            shopifyAdminGraphqlClient
        );

        var response = service.lookup(
            "alpha.myshopify.com",
            new ShopifyStorefrontOrderLookupRequest("#1001", "shopper@example.com", "order-lookup")
        );

        assertThat(response.available()).isTrue();
        assertThat(response.matched()).isTrue();
        assertThat(response.order()).isNotNull();
        assertThat(response.order().orderName()).isEqualTo("#1001");
        assertThat(response.order().trackingNumberMasked()).endsWith("9999");
        assertThat(response.order().lineItems()).hasSize(1);
    }

    @Test
    void blocksLookupWhenReadOrdersScopeIsMissing() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(true, List.of("ai-search", "order-lookup")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies")));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord("read_products,read_content,read_legal_policies")));
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", "access-token", List.of("ai-search", "order-lookup")))
            .thenReturn(List.of("ai-search", "order-lookup"));

        ShopifyStorefrontOrderLookupService service = new ShopifyStorefrontOrderLookupService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            shopifyAdminGraphqlClient
        );

        var response = service.lookup(
            "alpha.myshopify.com",
            new ShopifyStorefrontOrderLookupRequest("#1001", "shopper@example.com", "order-lookup")
        );

        assertThat(response.available()).isFalse();
        assertThat(response.status()).isEqualTo("SCOPE_REQUIRED");
        assertThat(response.matched()).isFalse();
    }

    @Test
    void blocksLookupWhenCurrentTierDoesNotAllowOrderLookup() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(true, List.of("ai-search", "order-lookup")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies,read_orders")));
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", "access-token", List.of("ai-search", "order-lookup")))
            .thenReturn(List.of("ai-search"));

        ShopifyStorefrontOrderLookupService service = new ShopifyStorefrontOrderLookupService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            shopifyAdminGraphqlClient
        );

        var response = service.lookup(
            "alpha.myshopify.com",
            new ShopifyStorefrontOrderLookupRequest("#1001", "shopper@example.com", "order-lookup")
        );

        assertThat(response.available()).isFalse();
        assertThat(response.status()).isEqualTo("SURFACE_NOT_ENABLED");
        assertThat(response.matched()).isFalse();
        verify(shopifyAdminGraphqlClient, never()).execute(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void keepsLookupOnScopeRequiredWhenSupportGateBlocksOnlyLaunchReadiness() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(false, List.of("ai-search", "order-lookup")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies")));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord("read_products,read_content,read_legal_policies")));
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", "access-token", List.of("ai-search", "order-lookup")))
            .thenReturn(List.of("ai-search", "order-lookup"));

        ShopifyStorefrontOrderLookupService service = new ShopifyStorefrontOrderLookupService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            shopifyAdminGraphqlClient
        );

        var response = service.lookup(
            "alpha.myshopify.com",
            new ShopifyStorefrontOrderLookupRequest("#1001", "shopper@example.com", "order-lookup")
        );

        assertThat(response.available()).isFalse();
        assertThat(response.status()).isEqualTo("SCOPE_REQUIRED");
    }

    @Test
    void prefersLiveCredentialScopesOverStalePersistedInstallRecordScopes() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyAdminGraphqlClient shopifyAdminGraphqlClient = mock(ShopifyAdminGraphqlClient.class);

        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store(true, List.of("ai-search", "order-lookup")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition("read_products,read_content,read_legal_policies,read_orders")));
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(installRecord("read_products,read_content,read_legal_policies")));
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", "access-token", List.of("ai-search", "order-lookup")))
            .thenReturn(List.of("ai-search", "order-lookup"));
        when(shopifyAdminGraphqlClient.execute(eq("alpha.myshopify.com"), eq("access-token"), anyString(), anyMap())).thenReturn(Map.of(
            "data", Map.of(
                "orders", Map.of("nodes", List.of())
            )
        ));

        ShopifyStorefrontOrderLookupService service = new ShopifyStorefrontOrderLookupService(
            platformClient,
            installCredentialService,
            installRecordService,
            billingService,
            shopifyAdminGraphqlClient
        );

        var response = service.lookup(
            "alpha.myshopify.com",
            new ShopifyStorefrontOrderLookupRequest("#1001", "shopper@example.com", "order-lookup")
        );

        assertThat(response.available()).isTrue();
        assertThat(response.status()).isEqualTo("NO_MATCH");
        assertThat(response.matched()).isFalse();
    }

    private ShopifyBridgeStoreSummary store(boolean storefrontReady, List<String> enabledSurfaces) {
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
                    enabledSurfaces,
                    "navigator",
                    List.of("navigator", "executor"),
                    java.util.Map.of("account", "executor")
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
            false,
            now,
            null,
            null,
            List.of(),
            now,
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
}
