package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeStoreLifecycleServiceTest {

    @Test
    void markUninstalledUpsertsBoundStoreState() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeStoreLifecycleService service = new ShopifyBridgeStoreLifecycleService(client);

        ShopifyBridgeStoreSummary current = store("INSTALLED", "SYNCED", "ENABLED", "LIVE");
        when(client.getStore("alpha.myshopify.com")).thenReturn(current);
        when(client.upsertStore(any())).thenReturn(store("UNINSTALLED", "NOT_SYNCED", "NOT_ENABLED", "BLOCKED"));

        ShopifyBridgeStoreSummary result = service.markUninstalled("alpha.myshopify.com");

        assertThat(result.installStatus()).isEqualTo("UNINSTALLED");
        verify(client).upsertStore(new ShopifyBridgeUpsertStoreRequest(
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "cust-1",
            "dep-1",
            "consumer-alpha",
            "UNINSTALLED",
            "NOT_SYNCED",
            "READY",
            "NOT_ENABLED",
            "BLOCKED",
            true,
            true,
            false,
            true
        ));
    }

    @Test
    void markUninstalledReturnsNullWhenStoreDoesNotExist() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeStoreLifecycleService service = new ShopifyBridgeStoreLifecycleService(client);

        when(client.getStore("missing.myshopify.com")).thenThrow(HttpClientErrorException.NotFound.create(
            HttpStatus.NOT_FOUND,
            "Not Found",
            HttpHeaders.EMPTY,
            new byte[0],
            null
        ));

        assertThat(service.markUninstalled("missing.myshopify.com")).isNull();
    }

    private ShopifyBridgeStoreSummary store(String installStatus, String syncStatus, String widgetStatus, String onboardingStatus) {
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
            "consumer-alpha",
            "Alpha Storefront",
            installStatus,
            syncStatus,
            "READY",
            widgetStatus,
            onboardingStatus,
            true,
            true,
            false,
            true,
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
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }
}
