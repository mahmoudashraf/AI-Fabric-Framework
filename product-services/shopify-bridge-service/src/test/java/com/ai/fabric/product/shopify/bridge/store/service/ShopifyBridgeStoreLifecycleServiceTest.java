package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWebhookEventRequest;
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

        when(client.markUninstalled("alpha.myshopify.com")).thenReturn(store("UNINSTALLED", "NOT_SYNCED", "NOT_ENABLED", "BLOCKED"));

        ShopifyBridgeStoreSummary result = service.markUninstalled("alpha.myshopify.com");

        assertThat(result.installStatus()).isEqualTo("UNINSTALLED");
        verify(client).markUninstalled("alpha.myshopify.com");
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

    @Test
    void recordWebhookEventDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeStoreLifecycleService service = new ShopifyBridgeStoreLifecycleService(client);

        when(client.recordWebhookEvent(any(), any())).thenReturn(store("INSTALLED", "NOT_SYNCED", "NOT_ENABLED", "PLATFORM_BOOTSTRAPPED"));

        ShopifyBridgeStoreSummary result = service.recordWebhookEvent(
            "alpha.myshopify.com",
            "products/update",
            "CONTENT_CHANGED",
            "products",
            "UPDATE",
            "gid://shopify/Product/1",
            "2026-04-22T00:00:00Z",
            "wh_123",
            "checksum-123",
            1,
            "Shopify product content changed. Incremental sync is required.",
            true
        );

        assertThat(result.syncStatus()).isEqualTo("NOT_SYNCED");
        verify(client).recordWebhookEvent(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordWebhookEventRequest(
                "products/update",
                "CONTENT_CHANGED",
                "products",
                "UPDATE",
                "gid://shopify/Product/1",
                "2026-04-22T00:00:00Z",
                "wh_123",
                "checksum-123",
                1,
                "Shopify product content changed. Incremental sync is required.",
                true
            )
        );
    }

    @Test
    void deleteStoreMappingDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeStoreLifecycleService service = new ShopifyBridgeStoreLifecycleService(client);

        service.deleteStoreMapping("alpha.myshopify.com", true);

        verify(client).deleteStore("alpha.myshopify.com", true);
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
