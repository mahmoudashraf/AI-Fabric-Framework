package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordBillingStateRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeStoreAdminServiceTest {

    @Test
    void runSourcePreflightUsesPersistedMaterialWhenAvailable() {
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeSourcePreflightService sourcePreflightService = mock(ShopifyBridgeSourcePreflightService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyBridgeVectorizationSourceService vectorizationSourceService = mock(ShopifyBridgeVectorizationSourceService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeStoreAdminService service = new ShopifyBridgeStoreAdminService(
            platformShopifyStoreClient,
            installCredentialService,
            mock(ShopifyInstallRecordService.class),
            billingService,
            sourcePreflightService,
            storeSyncService,
            vectorizationSourceService,
            usageService,
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );

        ShopifyBridgeStoreSummary store = sampleStore();
        ShopifyBridgeCredentialAcquisition acquisition = new ShopifyBridgeCredentialAcquisition(
            store,
            new ShopifyTokenExchangeMaterial(
                "access-token",
                null,
                null,
                null,
                "read_products,read_content,read_legal_policies",
                false
            )
        );

        when(installCredentialService.resolvePersistedMaterial(store.shopDomain())).thenReturn(Optional.of(acquisition));
        when(sourcePreflightService.run(acquisition)).thenReturn(store);

        ShopifyBridgeStoreSummary result = service.runSourcePreflight(store.shopDomain());

        assertThat(result.shopDomain()).isEqualTo(store.shopDomain());
        verify(sourcePreflightService).run(acquisition);
    }

    @Test
    void runSourcePreflightRejectsWhenPersistedMaterialIsMissing() {
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeSourcePreflightService sourcePreflightService = mock(ShopifyBridgeSourcePreflightService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyBridgeVectorizationSourceService vectorizationSourceService = mock(ShopifyBridgeVectorizationSourceService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeStoreAdminService service = new ShopifyBridgeStoreAdminService(
            platformShopifyStoreClient,
            installCredentialService,
            mock(ShopifyInstallRecordService.class),
            billingService,
            sourcePreflightService,
            storeSyncService,
            vectorizationSourceService,
            usageService,
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );

        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.runSourcePreflight("alpha.myshopify.com"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("persisted store credentials");
    }

    @Test
    void recordBillingStateRequiresRegisteredInstalledStoreAndPersistsTier() {
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeStoreAdminService service = new ShopifyBridgeStoreAdminService(
            platformShopifyStoreClient,
            installCredentialService,
            installRecordService,
            billingService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyBridgeVectorizationSourceService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(platformShopifyStoreClient.getStore("alpha.myshopify.com")).thenReturn(sampleStore());
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(new ShopifyBridgeBillingSummary(
            "FREE",
            "ELITE",
            "Loom Companion Elite",
            "ACTIVE",
            false,
            false,
            true,
            true,
            null,
            "HOURLY",
            false,
            true,
            true,
            true,
            List.of("guided-commerce"),
            List.of("ai-search", "comparison", "order-lookup"),
            List.of(),
            "Elite tier is active for this store from recorded Shopify billing state."
        ));

        ShopifyBridgeBillingSummary summary = service.recordBillingState(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordBillingStateRequest("ELITE", "ACTIVE", null, null, "live test activation")
        );

        assertThat(summary.tierKey()).isEqualTo("ELITE");
        verify(platformShopifyStoreClient).recordBillingState(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordBillingStateRequest("ELITE", "ACTIVE", null, null, "live test activation")
        );
        verify(installRecordService).recordBillingState(
            "alpha.myshopify.com",
            "ELITE",
            "ACTIVE",
            List.of(new ShopifyBridgeSupportSubscriptionSummary(
                "recorded-shopify-billing-elite",
                "Loom Companion Elite",
                "ACTIVE",
                "ELITE",
                true
            ))
        );
    }

    private ShopifyBridgeStoreSummary sampleStore() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-test",
            "Shopify Bridge Test",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "DRAFT",
            "consumer-1",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "PLATFORM_BOOTSTRAPPED",
            true,
            true,
            true,
            true,
            false,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:00Z")
        );
    }
}
