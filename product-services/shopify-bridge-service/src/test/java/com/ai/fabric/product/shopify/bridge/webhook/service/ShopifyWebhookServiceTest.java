package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreSyncService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShopifyWebhookServiceTest {

    @Test
    void appUninstalledUsesHeaderShopDomain() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, storeSyncService, new ObjectMapper());

        service.handle("app/uninstalled", "alpha.myshopify.com", "{\"myshopify_domain\":\"ignored.myshopify.com\"}");

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearLocalPersistedCredentials("alpha.myshopify.com");
        verify(installRecordService).markUninstalled("alpha.myshopify.com");
        verify(lifecycleService).recordWebhookEvent(
            "alpha.myshopify.com",
            "app/uninstalled",
            "UNINSTALLED",
            null,
            "Shopify reported app uninstall.",
            false
        );
        verifyNoInteractions(storeSyncService);
    }

    @Test
    void contentChangeWebhookInvalidatesSyncAndTriggersRefreshWhenCredentialsExist() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyBridgeCredentialAcquisition acquisition = acquisition();
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition));
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, storeSyncService, new ObjectMapper());

        service.handle("products/update", "alpha.myshopify.com", "{}");

        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("products/update"),
            eq("CONTENT_CHANGED"),
            eq("products"),
            eq("Shopify product content changed. Incremental sync is required."),
            eq(true)
        );
        verify(installCredentialService).resolvePersistedMaterial("alpha.myshopify.com");
        verify(storeSyncService).syncFromWebhook(acquisition, "products/update");
        verifyNoInteractions(installRecordService);
    }

    @Test
    void customerRedactWebhookRecordsComplianceEvent() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, storeSyncService, new ObjectMapper());

        service.handle("customers/redact", "alpha.myshopify.com", "{}");

        verify(lifecycleService).recordWebhookEvent(
            "alpha.myshopify.com",
            "customers/redact",
            "COMPLIANCE_CUSTOMER_REDACT",
            "privacy",
            "Shopify requested customer redaction. The bridge service does not retain customer records locally.",
            false
        );
        verifyNoInteractions(installCredentialService);
        verifyNoInteractions(installRecordService);
        verifyNoInteractions(storeSyncService);
    }

    @Test
    void appSubscriptionsUpdateRecordsBillingEvent() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, storeSyncService, new ObjectMapper());

        service.handle("app_subscriptions/update", "alpha.myshopify.com", "{}");

        verify(lifecycleService).recordWebhookEvent(
            "alpha.myshopify.com",
            "app_subscriptions/update",
            "BILLING_CHANGED",
            "billing",
            "Shopify app subscription billing changed. Review merchant billing status before go-live.",
            false
        );
        verifyNoInteractions(installCredentialService);
        verifyNoInteractions(installRecordService);
        verifyNoInteractions(storeSyncService);
    }

    @Test
    void shopRedactWebhookTriggersCleanup() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, storeSyncService, new ObjectMapper());

        service.handle("shop/redact", "alpha.myshopify.com", "{}");

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearLocalPersistedCredentials("alpha.myshopify.com");
        verify(installRecordService).markUninstalled("alpha.myshopify.com");
        verify(lifecycleService).recordWebhookEvent(
            "alpha.myshopify.com",
            "shop/redact",
            "COMPLIANCE_SHOP_REDACT",
            "privacy",
            "Shopify requested shop redaction. Credentials and store mapping cleanup have been triggered.",
            false
        );
        verify(lifecycleService).deleteStoreMapping("alpha.myshopify.com", true);
        verify(installRecordService).deleteRecord("alpha.myshopify.com");
        verifyNoInteractions(storeSyncService);
    }

    @Test
    void unknownTopicDoesNothing() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(lifecycleService, installRecordService, installCredentialService, storeSyncService, new ObjectMapper());

        service.handle("orders/paid", "alpha.myshopify.com", "{}");

        verifyNoInteractions(lifecycleService);
        verifyNoInteractions(installCredentialService);
        verifyNoInteractions(installRecordService);
        verifyNoInteractions(storeSyncService);
    }

    private ShopifyBridgeCredentialAcquisition acquisition() {
        ShopifyBridgeStoreSummary store = new ShopifyBridgeStoreSummary(
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
            "consumer-alpha",
            "Alpha Storefront",
            "INSTALLED",
            "SYNCED",
            "READY",
            "NOT_ENABLED",
            "PLATFORM_BOOTSTRAPPED",
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
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content",
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
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
        return new ShopifyBridgeCredentialAcquisition(
            store,
            new ShopifyTokenExchangeMaterial(
                "shpat_access",
                "shprt_refresh",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content",
                true
            )
        );
    }
}
