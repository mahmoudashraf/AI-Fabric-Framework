package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeStoreBillingState;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(
            lifecycleService,
            installRecordService,
            installCredentialService,
            platformShopifyStoreClient,
            billingService,
            storeSyncService,
            new ObjectMapper()
        );

        service.handle("app/uninstalled", "alpha.myshopify.com", "wh_1", "{\"myshopify_domain\":\"ignored.myshopify.com\"}");

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearLocalPersistedCredentials("alpha.myshopify.com");
        verify(installRecordService).markUninstalled("alpha.myshopify.com");
        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("app/uninstalled"),
            eq("UNINSTALLED"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq("wh_1"),
            anyString(),
            isNull(),
            eq("Shopify reported app uninstall."),
            eq(false)
        );
        verifyNoInteractions(storeSyncService);
    }

    @Test
    void contentChangeWebhookInvalidatesSyncAndTriggersRefreshWhenCredentialsExist() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyBridgeCredentialAcquisition acquisition = acquisition();
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.of(acquisition));
        ShopifyWebhookService service = new ShopifyWebhookService(
            lifecycleService,
            installRecordService,
            installCredentialService,
            platformShopifyStoreClient,
            billingService,
            storeSyncService,
            new ObjectMapper()
        );

        service.handle(
            "products/update",
            "alpha.myshopify.com",
            "wh_2",
            "{\"admin_graphql_api_id\":\"gid://shopify/Product/42\",\"updatedAt\":\"2026-04-22T01:02:03Z\"}"
        );

        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("products/update"),
            eq("CONTENT_CHANGED"),
            eq("products"),
            eq("UPDATE"),
            eq("gid://shopify/Product/42"),
            eq("2026-04-22T01:02:03Z"),
            eq("wh_2"),
            anyString(),
            isNull(),
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
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(
            lifecycleService,
            installRecordService,
            installCredentialService,
            platformShopifyStoreClient,
            billingService,
            storeSyncService,
            new ObjectMapper()
        );

        service.handle("customers/redact", "alpha.myshopify.com", "wh_3", "{}");

        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("customers/redact"),
            eq("COMPLIANCE_CUSTOMER_REDACT"),
            eq("privacy"),
            isNull(),
            isNull(),
            isNull(),
            eq("wh_3"),
            anyString(),
            isNull(),
            eq("Shopify requested customer redaction. The bridge service does not retain customer records locally."),
            eq(false)
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
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(
            lifecycleService,
            installRecordService,
            installCredentialService,
            platformShopifyStoreClient,
            billingService,
            storeSyncService,
            new ObjectMapper()
        );
        when(platformShopifyStoreClient.resolveCredentialMaterial("alpha.myshopify.com")).thenReturn(new ShopifyBridgeResolvedStoreCredentials(
            "shpat_access",
            null,
            null,
            null,
            "read_products,read_orders",
            false
        ));
        when(billingService.inspectStoreBillingState("alpha.myshopify.com", "shpat_access"))
            .thenReturn(new ShopifyBridgeStoreBillingState(
                "STARTER",
                "ACTIVE",
                List.of(new ShopifyBridgeSupportSubscriptionSummary(
                    "gid://shopify/AppSubscription/1",
                    "Loom Companion Starter",
                    "ACTIVE",
                    "STARTER",
                    true
                ))
            ));

        service.handle("app_subscriptions/update", "alpha.myshopify.com", "wh_4", "{}");

        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("app_subscriptions/update"),
            eq("BILLING_CHANGED"),
            eq("billing"),
            isNull(),
            isNull(),
            isNull(),
            eq("wh_4"),
            anyString(),
            isNull(),
            eq("Shopify app subscription billing changed. Review merchant billing status before go-live."),
            eq(false)
        );
        verify(platformShopifyStoreClient).resolveCredentialMaterial("alpha.myshopify.com");
        verify(installRecordService).recordBillingState(eq("alpha.myshopify.com"), eq("STARTER"), eq("ACTIVE"), any());
        verifyNoInteractions(installCredentialService);
        verifyNoInteractions(storeSyncService);
    }

    @Test
    void shopRedactWebhookTriggersCleanup() {
        ShopifyBridgeStoreLifecycleService lifecycleService = mock(ShopifyBridgeStoreLifecycleService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(
            lifecycleService,
            installRecordService,
            installCredentialService,
            platformShopifyStoreClient,
            billingService,
            storeSyncService,
            new ObjectMapper()
        );

        service.handle("shop/redact", "alpha.myshopify.com", "wh_5", "{}");

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearLocalPersistedCredentials("alpha.myshopify.com");
        verify(installRecordService).markUninstalled("alpha.myshopify.com");
        verify(lifecycleService).recordWebhookEvent(
            eq("alpha.myshopify.com"),
            eq("shop/redact"),
            eq("COMPLIANCE_SHOP_REDACT"),
            eq("privacy"),
            isNull(),
            isNull(),
            isNull(),
            eq("wh_5"),
            anyString(),
            isNull(),
            eq("Shopify requested shop redaction. Credentials and store mapping cleanup have been triggered."),
            eq(false)
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
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyWebhookService service = new ShopifyWebhookService(
            lifecycleService,
            installRecordService,
            installCredentialService,
            platformShopifyStoreClient,
            billingService,
            storeSyncService,
            new ObjectMapper()
        );

        service.handle("orders/paid", "alpha.myshopify.com", "wh_6", "{}");

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
                "read_products,read_content,read_legal_policies",
                true
            )
        );
    }
}
