package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ShopifyWebhookServiceTest {

    @Test
    void appUninstalledUsesHeaderShopDomain() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, new ObjectMapper());

        service.handle("app/uninstalled", "alpha.myshopify.com", "{\"myshopify_domain\":\"ignored.myshopify.com\"}");

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearPersistedCredentials("alpha.myshopify.com");
        verify(installRecordService).markUninstalled("alpha.myshopify.com");
        verify(lifecycleService).recordWebhookEvent(
            "alpha.myshopify.com",
            "app/uninstalled",
            "UNINSTALLED",
            null,
            "Shopify reported app uninstall.",
            false
        );
    }

    @Test
    void contentChangeWebhookInvalidatesSync() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, new ObjectMapper());

        service.handle("products/update", "alpha.myshopify.com", "{}");

        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("products/update"),
            eq("CONTENT_CHANGED"),
            eq("products"),
            eq("Shopify product content changed. Incremental sync is required."),
            eq(true)
        );
        verifyNoInteractions(installCredentialService);
        verifyNoInteractions(installRecordService);
    }

    @Test
    void unknownTopicDoesNothing() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, new ObjectMapper());

        service.handle("orders/paid", "alpha.myshopify.com", "{}");

        verifyNoInteractions(lifecycleService);
        verifyNoInteractions(installCredentialService);
        verifyNoInteractions(installRecordService);
    }
}
