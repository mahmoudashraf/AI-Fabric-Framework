package com.ai.fabric.product.shopify.bridge.diagnostics.service;

import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageEventCountSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.entity.ShopifyInstallRecordEntity;
import com.ai.fabric.product.shopify.bridge.install.repository.ShopifyInstallRecordRepository;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyBridgeDiagnosticsServiceTest {

    @Test
    void overviewAggregatesInstallAndStoreCounts() {
        ShopifyInstallRecordRepository installRecordRepository = mock(ShopifyInstallRecordRepository.class);
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);
        when(installRecordRepository.findAll()).thenReturn(List.of(
            install("alpha.myshopify.com", "INSTALLED", true),
            install("beta.myshopify.com", "UNINSTALLED", false)
        ));
        when(platformShopifyStoreClient.listStores()).thenReturn(List.of(
            store("alpha.myshopify.com", "LIVE", true, true, Instant.parse("2026-04-18T10:15:00Z")),
            store("beta.myshopify.com", "BLOCKED", false, false, Instant.parse("2026-04-18T09:15:00Z"))
        ));
        when(billingService.summarize()).thenReturn(new ShopifyBridgeBillingSummary(
            "FREE",
            "Companion Free",
            "ACTIVE",
            false,
            false,
            "Free mode."
        ));
        when(usageService.summarizeAllShops()).thenReturn(new ShopifyBridgeUsageOverview(
            Instant.parse("2026-04-18T10:20:00Z"),
            Instant.parse("2026-04-18T10:18:00Z"),
            1,
            2,
            5,
            8,
            List.of(new ShopifyBridgeUsageEventCountSummary("MERCHANT_GO_LIVE", 2)),
            List.of(new ShopifyBridgeUsageEventCountSummary("STOREFRONT_WIDGET_OPENED_HOME_PAGE", 4))
        ));
        when(webhookSubscriptionService.expectedWebhookUri()).thenReturn("https://bridge.example.com/api/webhooks/shopify");
        when(webhookSubscriptionService.expectedSubscriptionCount()).thenReturn(11);
        when(webhookSubscriptionService.expectedTopics()).thenReturn(List.of("APP_UNINSTALLED", "PRODUCTS_CREATE"));

        ShopifyBridgeDiagnosticsService service = new ShopifyBridgeDiagnosticsService(
            properties(),
            installRecordRepository,
            platformShopifyStoreClient,
            billingService,
            usageService,
            webhookSubscriptionService
        );

        var overview = service.overview();

        assertThat(overview.status()).isEqualTo("READY");
        assertThat(overview.installs().totalCount()).isEqualTo(2);
        assertThat(overview.installs().credentialReadyCount()).isEqualTo(1);
        assertThat(overview.stores().totalCount()).isEqualTo(2);
        assertThat(overview.stores().readyForGoLiveCount()).isEqualTo(1);
        assertThat(overview.stores().blockedCount()).isEqualTo(1);
        assertThat(overview.webhookSubscriptions().status()).isEqualTo("READY");
        assertThat(overview.webhookSubscriptions().expectedCount()).isEqualTo(11);
        assertThat(overview.billing().mode()).isEqualTo("FREE");
        assertThat(overview.usage().activeShopsLast7Days()).isEqualTo(2);
        assertThat(overview.usage().totalToday()).isEqualTo(5);
        assertThat(overview.notYetImplemented()).isEmpty();
    }

    @Test
    void overviewDegradesWhenPlatformStoreLookupFails() {
        ShopifyInstallRecordRepository installRecordRepository = mock(ShopifyInstallRecordRepository.class);
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyWebhookSubscriptionService webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);
        when(installRecordRepository.findAll()).thenReturn(List.of());
        when(platformShopifyStoreClient.listStores()).thenThrow(new IllegalStateException("Platform store API unavailable"));
        when(billingService.summarize()).thenReturn(new ShopifyBridgeBillingSummary(
            "FREE",
            "Companion Free",
            "ACTIVE",
            false,
            false,
            "Free mode."
        ));
        when(usageService.summarizeAllShops()).thenReturn(new ShopifyBridgeUsageOverview(
            Instant.parse("2026-04-18T10:20:00Z"),
            null,
            0,
            0,
            0,
            0,
            List.of(),
            List.of()
        ));
        when(webhookSubscriptionService.expectedSubscriptionCount()).thenReturn(11);
        when(webhookSubscriptionService.expectedTopics()).thenReturn(List.of("APP_UNINSTALLED"));
        when(webhookSubscriptionService.expectedWebhookUri()).thenThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "Shopify Bridge public base URL is not configured."
        ));

        ShopifyBridgeDiagnosticsService service = new ShopifyBridgeDiagnosticsService(
            properties(),
            installRecordRepository,
            platformShopifyStoreClient,
            billingService,
            usageService,
            webhookSubscriptionService
        );

        var overview = service.overview();

        assertThat(overview.status()).isEqualTo("DEGRADED");
        assertThat(overview.stores().platformAccessStatus()).isEqualTo("FAILED");
        assertThat(overview.stores().platformAccessMessage()).contains("Platform store API unavailable");
        assertThat(overview.webhookSubscriptions().status()).isEqualTo("BLOCKED");
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Shopify Bridge Service",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-api-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-shared-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private ShopifyInstallRecordEntity install(String shopDomain, String status, boolean credentialsReady) {
        ShopifyInstallRecordEntity entity = new ShopifyInstallRecordEntity();
        entity.setId("sir-" + shopDomain.replace(".", "-"));
        entity.setShopDomain(shopDomain);
        entity.setStatus(status);
        entity.setAccessTokenSecretRef(credentialsReady ? "MANAGED_TOKEN_" + shopDomain.toUpperCase().replace('.', '_') : null);
        entity.setLastAuthenticatedAt(Instant.parse("2026-04-18T10:10:00Z"));
        if ("UNINSTALLED".equals(status)) {
            entity.setLastUninstalledAt(Instant.parse("2026-04-18T09:00:00Z"));
        }
        return entity;
    }

    private ShopifyBridgeStoreSummary store(String shopDomain,
                                            String onboardingStatus,
                                            boolean goLiveEligible,
                                            boolean storefrontReady,
                                            Instant lastWebhookAt) {
        return new ShopifyBridgeStoreSummary(
            "shp-" + shopDomain.replace(".", "-"),
            shopDomain,
            shopDomain,
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Customer",
            "dep-1",
            "Deployment",
            "ACTIVE",
            "consumer-1",
            "Consumer",
            "INSTALLED",
            storefrontReady ? "SYNCED" : "NOT_SYNCED",
            goLiveEligible ? "READY" : "NOT_RUN",
            storefrontReady ? "ENABLED" : "NOT_ENABLED",
            onboardingStatus,
            true,
            true,
            true,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            new ShopifyBridgeStoreReadinessSummary(
                storefrontReady ? "STOREFRONT_READY" : goLiveEligible ? "READY_FOR_GO_LIVE" : "BLOCKED",
                goLiveEligible,
                storefrontReady,
                goLiveEligible ? List.of() : List.of("Shopify source readiness is not READY yet."),
                storefrontReady ? List.of() : List.of("No verified deployment release exists yet."),
                storefrontReady ? List.of() : List.of("Request go-live to publish, apply, and verify the deployment.")
            ),
            null,
            null,
            null,
            null,
            lastWebhookAt,
            Instant.parse("2026-04-18T08:00:00Z"),
            Instant.parse("2026-04-18T08:00:00Z")
        );
    }
}
