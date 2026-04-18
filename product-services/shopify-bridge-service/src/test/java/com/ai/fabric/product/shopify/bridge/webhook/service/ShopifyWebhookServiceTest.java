package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShopifyWebhookServiceTest {

    @Test
    void appUninstalledUsesHeaderShopDomain() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, new ObjectMapper());

        service.handle("app/uninstalled", "alpha.myshopify.com", "{\"myshopify_domain\":\"ignored.myshopify.com\"}");

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
    }

    @Test
    void unknownTopicDoesNothing() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, new ObjectMapper());

        service.handle("products/update", "alpha.myshopify.com", "{}");

        verifyNoInteractions(lifecycleService);
    }
}
