package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeInstallCredentialServiceTest {

    private ShopifyTokenExchangeService tokenExchangeService;
    private PlatformShopifyStoreClient platformClient;
    private ShopifyInstallRecordService installRecordService;
    private ShopifyWebhookSubscriptionService webhookSubscriptionService;
    private ShopifyBridgeInstallCredentialService service;

    @BeforeEach
    void setUp() {
        tokenExchangeService = mock(ShopifyTokenExchangeService.class);
        platformClient = mock(PlatformShopifyStoreClient.class);
        installRecordService = mock(ShopifyInstallRecordService.class);
        webhookSubscriptionService = mock(ShopifyWebhookSubscriptionService.class);
        service = new ShopifyBridgeInstallCredentialService(
            tokenExchangeService,
            platformClient,
            installRecordService,
            webhookSubscriptionService
        );
    }

    @Test
    void resolvePersistedMaterialReturnsCredentialAcquisition() {
        ShopifyBridgeStoreSummary store = store();
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store);
        when(platformClient.resolveCredentialMaterial("alpha.myshopify.com")).thenReturn(
            new ShopifyBridgeResolvedStoreCredentials(
                "shpat_access",
                "shprt_refresh",
                Instant.parse("2099-04-18T01:00:00Z"),
                Instant.parse("2099-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            )
        );

        Optional<ShopifyBridgeCredentialAcquisition> resolved = service.resolvePersistedMaterial("alpha.myshopify.com");

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().store().shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(resolved.orElseThrow().tokenExchangeMaterial().accessToken()).isEqualTo("shpat_access");
        assertThat(resolved.orElseThrow().tokenExchangeMaterial().refreshToken()).isEqualTo("shprt_refresh");
        verify(platformClient).getStore("alpha.myshopify.com");
        verify(platformClient).resolveCredentialMaterial("alpha.myshopify.com");
    }

    @Test
    void resolvePersistedMaterialRefreshesExpiredExpiringToken() {
        ShopifyBridgeStoreSummary store = store();
        ShopifyBridgeStoreSummary refreshedStore = storeWithCredentialTimes(
            Instant.parse("2026-04-19T12:00:00Z"),
            Instant.parse("2026-07-18T12:00:00Z")
        );
        when(platformClient.getStore("alpha.myshopify.com"))
            .thenReturn(store)
            .thenReturn(refreshedStore);
        when(platformClient.resolveCredentialMaterial("alpha.myshopify.com")).thenReturn(
            new ShopifyBridgeResolvedStoreCredentials(
                "shpat_expired",
                "shprt_refresh",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            )
        );
        when(tokenExchangeService.refreshExpiringOfflineToken("alpha.myshopify.com", "shprt_refresh")).thenReturn(
            new ShopifyTokenExchangeMaterial(
                "shpat_rotated",
                "shprt_rotated",
                Instant.parse("2026-04-19T12:00:00Z"),
                Instant.parse("2026-07-18T12:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            )
        );
        when(platformClient.upsertCredentials(eq("alpha.myshopify.com"), any())).thenReturn(refreshedStore);

        Optional<ShopifyBridgeCredentialAcquisition> resolved = service.resolvePersistedMaterial("alpha.myshopify.com");

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().tokenExchangeMaterial().accessToken()).isEqualTo("shpat_rotated");
        assertThat(resolved.orElseThrow().tokenExchangeMaterial().refreshToken()).isEqualTo("shprt_rotated");
        verify(tokenExchangeService).refreshExpiringOfflineToken("alpha.myshopify.com", "shprt_refresh");
        verify(platformClient).upsertCredentials(eq("alpha.myshopify.com"), any());
    }

    @Test
    void acquireAndPersistMaterialReconcilesWebhookSubscriptions() {
        ShopifyBridgeStoreSummary store = store();
        when(tokenExchangeService.exchangeExpiringOfflineToken(eq(session()), eq("Bearer session-token"))).thenReturn(
            new ShopifyTokenExchangeMaterial(
                "shpat_access",
                "shprt_refresh",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            )
        );
        when(platformClient.upsertCredentials(eq("alpha.myshopify.com"), org.mockito.ArgumentMatchers.any())).thenReturn(store);

        ShopifyBridgeCredentialAcquisition acquisition = service.acquireAndPersistMaterial(session(), "Bearer session-token");

        assertThat(acquisition.tokenExchangeMaterial().accessToken()).isEqualTo("shpat_access");
        verify(webhookSubscriptionService).reconcileContentSubscriptions("alpha.myshopify.com", "shpat_access");
    }

    @Test
    void reconcileWebhookSubscriptionsUsesPersistedCredentialAndRefreshesAppScopesReadiness() {
        ShopifyBridgeStoreSummary store = store();
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store);
        when(platformClient.resolveCredentialMaterial("alpha.myshopify.com")).thenReturn(
            new ShopifyBridgeResolvedStoreCredentials(
                "shpat_access",
                null,
                Instant.parse("2099-04-18T01:00:00Z"),
                null,
                "read_products,read_content,read_legal_policies",
                false
            )
        );
        when(webhookSubscriptionService.inspectContentSubscriptions("alpha.myshopify.com", "shpat_access")).thenReturn(
            new ShopifyWebhookSubscriptionStatusSummary(
                "alpha.myshopify.com",
                "READY",
                "All required Shopify webhook subscriptions are present.",
                "https://bridge.example.com/api/webhooks/shopify",
                1,
                1,
                0,
                0,
                Instant.parse("2026-04-18T10:00:00Z"),
                List.of(new ShopifyWebhookSubscriptionTopicStatusSummary(
                    "APP_SCOPES_UPDATE",
                    "loom-app-scopes-update",
                    "READY",
                    "gid://shopify/WebhookSubscription/1",
                    "loom-app-scopes-update",
                    "https://bridge.example.com/api/webhooks/shopify",
                    "Ready."
                ))
            )
        );
        when(webhookSubscriptionService.inspectTopicStatus("alpha.myshopify.com", "shpat_access", "APP_SCOPES_UPDATE")).thenReturn(
            new ShopifyWebhookSubscriptionTopicStatusSummary(
                "APP_SCOPES_UPDATE",
                "loom-app-scopes-update",
                "READY",
                "gid://shopify/WebhookSubscription/1",
                "loom-app-scopes-update",
                "https://bridge.example.com/api/webhooks/shopify",
                "Ready."
            )
        );

        ShopifyWebhookSubscriptionStatusSummary summary = service.reconcileWebhookSubscriptions("alpha.myshopify.com");

        assertThat(summary.status()).isEqualTo("READY");
        verify(webhookSubscriptionService).reconcileContentSubscriptions("alpha.myshopify.com", "shpat_access");
        verify(installRecordService).recordAppScopesUpdateWebhookReady("alpha.myshopify.com", true);
    }

    private com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession session() {
        return new com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://admin.shopify.com/store/alpha",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T01:00:00Z")
        );
    }

    private ShopifyBridgeStoreSummary store() {
        return storeWithCredentialTimes(
            Instant.parse("2026-04-18T01:00:00Z"),
            Instant.parse("2026-07-18T00:00:00Z")
        );
    }

    private ShopifyBridgeStoreSummary storeWithCredentialTimes(Instant accessTokenExpiresAt,
                                                               Instant refreshTokenExpiresAt) {
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
            "consumer-alpha",
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
            false,
            false,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA",
                Instant.parse("2026-04-18T00:00:00Z"),
                accessTokenExpiresAt,
                refreshTokenExpiresAt,
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
    }
}
