package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalRequest;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalResponse;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCapabilitySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationAutomationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationEventSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationIndexedFieldSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationPolicySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSelectedEntitiesRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSourcePolicyInput;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSourcePolicySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateStoreVectorizationPolicyRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSourceSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontPreviewService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionDiagnosticsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeMerchantStoreServiceTest {

    @Test
    void sessionReturnsStoreWhenPresent() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            installRecordService,
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installRecordService.recordAuthenticatedSession(session(), "host-token")).thenReturn(new ShopifyInstallRecordSummary(
            "alpha.myshopify.com",
            "INSTALLED",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            "host-token",
            "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
            "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
            "read_products",
            Instant.parse("2026-04-18T01:00:00Z"),
            Instant.parse("2026-07-18T00:00:00Z"),
            false,
            Instant.parse("2026-04-18T00:00:00Z"),
            null,
            null,
            List.of(),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            null
        ));

        var response = service.session(session(), "host-token");

        assertThat(response.installRecord()).isNotNull();
        assertThat(response.store()).isNotNull();
        assertThat(response.installRecoveryRequired()).isFalse();
        assertThat(response.store().shopDomain()).isEqualTo("alpha.myshopify.com");
    }

    @Test
    void sessionFlagsInstallRecoveryWhenShopWasUninstalled() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            installRecordService,
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(uninstalledStore("alpha.myshopify.com"));
        when(installRecordService.recordAuthenticatedSession(session(), "host-token")).thenReturn(new ShopifyInstallRecordSummary(
            "alpha.myshopify.com",
            "UNINSTALLED",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            "host-token",
            null,
            null,
            null,
            null,
            null,
            false,
            Instant.parse("2026-04-18T00:00:00Z"),
            null,
            null,
            List.of(),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T11:00:00Z")
        ));

        var response = service.session(session(), "host-token");

        assertThat(response.installRecoveryRequired()).isTrue();
        assertThat(response.installRecoveryUrl()).isEqualTo("https://bridge.example.com/auth/shopify/install?shop=alpha.myshopify.com");
        assertThat(response.installRecoveryMessage()).contains("complete the Shopify install flow again");
    }

    @Test
    void connectUpsertsWhenStoreDoesNotExist() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            billingService,
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenThrow(notFound());
        when(client.upsertStore(any())).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));

        ShopifyBridgeStoreSummary response = service.connect(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).upsertStore(new ShopifyBridgeUpsertStoreRequest(
            "alpha.myshopify.com",
            "alpha.myshopify.com",
            "shopify-bridge-prod",
            null,
            null,
            null,
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            true,
            true,
            true,
            true,
            true,
            true
        ));
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_CONNECT");
    }

    @Test
    void bootstrapReusesExistingStoreWhenAlreadyConnected() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            billingService,
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));
        when(client.bootstrap("alpha.myshopify.com")).thenReturn(new ShopifyBridgeStoreBootstrapResponse(
            "alpha.myshopify.com",
            "cust-1",
            "dep-1",
            "consumer-1",
            false,
            false,
            false,
            List.of("mkp-template-shopify-companion"),
            store("alpha.myshopify.com")
        ));

        ShopifyBridgeStoreBootstrapResponse response = service.bootstrap(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client, never()).upsertStore(any());
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(client).bootstrap("alpha.myshopify.com");
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_BOOTSTRAP");
    }

    @Test
    void goLiveUsesConnectedCredentialsAndDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            billingService,
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));
        when(billingService.summarizeForShop("alpha.myshopify.com", "shpat_access")).thenReturn(new ShopifyBridgeBillingSummary(
            "FREE",
            "FREE",
            "Companion Free",
            "ACTIVE",
            false,
            false,
            false,
            false,
            50,
            "DAILY",
            true,
            false,
            false,
            false,
            List.of(),
            List.of("ai-search"),
            List.of(),
            "Companion launch is available."
        ));
        when(client.goLive("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.goLive(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(billingService).summarizeForShop("alpha.myshopify.com", "shpat_access");
        verify(client).goLive("alpha.myshopify.com");
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_GO_LIVE");
    }

    @Test
    void goLiveRejectsWhenBillingLaunchIsBlocked() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            billingService,
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));
        when(billingService.summarizeForShop("alpha.myshopify.com", "shpat_access")).thenReturn(new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "STARTER",
            "Loom Companion Starter",
            "SETUP_REQUIRED",
            true,
            true,
            true,
            false,
            null,
            "TWO_HOURS",
            false,
            true,
            false,
            true,
            List.of(),
            List.of("ai-search", "contextual-pill"),
            List.of(),
            "Shopify billing setup is incomplete for this bridge environment."
        ));

        assertThatThrownBy(() -> service.goLive(session(), "Bearer session-token"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Shopify billing setup is incomplete");

        verify(client, never()).goLive("alpha.myshopify.com");
        verify(usageService, never()).recordEvent("alpha.myshopify.com", "MERCHANT_GO_LIVE");
    }

    @Test
    void billingSummaryUsesPersistedCredentialsWhenAvailable() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            billingService,
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com"))
            .thenReturn(java.util.Optional.of(acquisition(store("alpha.myshopify.com"))));
        when(billingService.summarizeForShop("alpha.myshopify.com", "shpat_access")).thenReturn(new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "FREE",
            "Loom Companion Free",
            "ACTIVE",
            false,
            false,
            false,
            false,
            50,
            "DAILY",
            true,
            false,
            false,
            false,
            List.of(),
            List.of("ai-search"),
            List.of(
                new ShopifyBridgeBillingPlanSummary(
                    "STARTER",
                    "Loom Companion Starter",
                    "29.00",
                    "USD",
                    "EVERY_30_DAYS",
                    false,
                    true,
                    true,
                    false,
                    null,
                    "TWO_HOURS",
                    false,
                    true,
                    false,
                    true,
                    List.of(),
                    List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison"),
                    "Available for merchant approval through Shopify billing."
                )
            ),
            "Free tier is active."
        ));

        ShopifyBridgeBillingSummary summary = service.billingSummary(session());

        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.tierKey()).isEqualTo("FREE");
        verify(billingService).summarizeForShop("alpha.myshopify.com", "shpat_access");
    }

    @Test
    void requestBillingApprovalUsesConnectedCredentialsAndRecordsUsage() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            billingService,
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token"))
            .thenReturn(acquisition(store("alpha.myshopify.com")));
        when(billingService.createApproval("alpha.myshopify.com", "shpat_access", "STARTER")).thenReturn(new ShopifyBridgeBillingApprovalResponse(
            "READY_FOR_APPROVAL",
            "https://alpha.myshopify.com/admin/charges/confirm",
            "Redirect the merchant to Shopify to approve the app subscription."
        ));

        ShopifyBridgeBillingApprovalResponse response = service.requestBillingApproval(
            session(),
            "Bearer session-token",
            new ShopifyBridgeBillingApprovalRequest("STARTER")
        );

        assertThat(response.confirmationUrl()).isEqualTo("https://alpha.myshopify.com/admin/charges/confirm");
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(billingService).createApproval("alpha.myshopify.com", "shpat_access", "STARTER");
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_BILLING_APPROVAL_REQUESTED");
    }

    @Test
    void runSourcePreflightUsesConnectedCredentials() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeSourcePreflightService sourcePreflightService = mock(ShopifyBridgeSourcePreflightService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            sourcePreflightService,
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        ShopifyBridgeCredentialAcquisition acquisition = acquisition(store("alpha.myshopify.com"));
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token")).thenReturn(acquisition);
        when(sourcePreflightService.run(acquisition)).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.runSourcePreflight(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(sourcePreflightService).run(acquisition);
    }

    @Test
    void syncNowUsesConnectedCredentials() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeStoreSyncService storeSyncService = mock(ShopifyBridgeStoreSyncService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            installCredentialService,
            mock(ShopifyBridgeSourcePreflightService.class),
            storeSyncService,
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        ShopifyBridgeCredentialAcquisition acquisition = acquisition(store("alpha.myshopify.com"));
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(installCredentialService.acquireAndPersistMaterial(session(), "Bearer session-token")).thenReturn(acquisition);
        when(storeSyncService.sync(acquisition)).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.syncNow(session(), "Bearer session-token");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(installCredentialService).acquireAndPersistMaterial(session(), "Bearer session-token");
        verify(storeSyncService).sync(acquisition);
    }

    @Test
    void updateSourceSettingsResetsReadinessAndSyncWhenTogglesChange() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.getStore("alpha.myshopify.com")).thenReturn(store("alpha.myshopify.com"));
        when(client.upsertStore(any())).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.updateSourceSettings(
            session(),
            new ShopifyBridgeUpdateSourceSettingsRequest(true, false, true, false, false, false)
        );

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).upsertStore(new ShopifyBridgeUpsertStoreRequest(
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "cust-1",
            "dep-1",
            "consumer-1",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "PLATFORM_BOOTSTRAPPED",
            true,
            false,
            true,
            false,
            false,
            false
        ));
        verify(client).enqueueProvisioning(eq("alpha.myshopify.com"), any());
    }

    @Test
    void vectorizeNowDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.vectorizeNow("alpha.myshopify.com")).thenReturn(vectorization("alpha.myshopify.com"));

        ShopifyBridgeStoreVectorizationSummary response = service.vectorizeNow(session());

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).vectorizeNow("alpha.myshopify.com");
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_VECTORIZATION_RUN_TRIGGERED");
    }

    @Test
    void indexAllDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.indexAllEnabledData("alpha.myshopify.com")).thenReturn(vectorization("alpha.myshopify.com"));

        ShopifyBridgeStoreVectorizationSummary response = service.indexAllEnabledData(session());

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).indexAllEnabledData("alpha.myshopify.com");
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_VECTORIZATION_INDEX_ALL");
    }

    @Test
    void updateVectorizationPolicyDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeUsageService usageService = mock(ShopifyBridgeUsageService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            usageService,
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        ShopifyBridgeUpdateStoreVectorizationPolicyRequest request = new ShopifyBridgeUpdateStoreVectorizationPolicyRequest(
            1L,
            List.of(new ShopifyBridgeStoreVectorizationSourcePolicyInput(
                "products",
                true,
                true,
                true,
                "INDEXED_FIELDS_ONLY",
                List.of("products.title"),
                30,
                60
            ))
        );
        when(client.updateVectorizationPolicy("alpha.myshopify.com", request)).thenReturn(vectorization("alpha.myshopify.com"));

        ShopifyBridgeStoreVectorizationSummary response = service.updateVectorizationPolicy(session(), request);

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).updateVectorizationPolicy("alpha.myshopify.com", request);
        verify(usageService).recordEvent("alpha.myshopify.com", "MERCHANT_VECTORIZATION_POLICY_UPDATED");
    }

    @Test
    void updateWidgetSettingsDelegatesToPlatform() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyWebhookSubscriptionDiagnosticsService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(client.updateWidgetSettings("alpha.myshopify.com", new ShopifyBridgeUpdateWidgetSettingsRequest(
            "Need help?",
            "Ask me about products and policies.",
            "GUIDED_COMMERCE",
            true,
            List.of("ai-search", "comparison"),
            "navigator",
            List.of("navigator", "executor"),
            java.util.Map.of("account", "executor")
        ))).thenReturn(store("alpha.myshopify.com"));

        ShopifyBridgeStoreSummary response = service.updateWidgetSettings(
            session(),
            new ShopifyBridgeUpdateWidgetSettingsRequest(
                "Need help?",
                "Ask me about products and policies.",
                "GUIDED_COMMERCE",
                true,
                List.of("ai-search", "comparison"),
                "navigator",
                List.of("navigator", "executor"),
                java.util.Map.of("account", "executor")
            )
        );

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        verify(client).updateWidgetSettings("alpha.myshopify.com", new ShopifyBridgeUpdateWidgetSettingsRequest(
            "Need help?",
            "Ask me about products and policies.",
            "GUIDED_COMMERCE",
            true,
            List.of("ai-search", "comparison"),
            "navigator",
            List.of("navigator", "executor"),
            java.util.Map.of("account", "executor")
        ));
    }

    @Test
    void webhookSubscriptionsUseDiagnosticsService() {
        PlatformShopifyStoreClient client = mock(PlatformShopifyStoreClient.class);
        ShopifyWebhookSubscriptionDiagnosticsService diagnosticsService = mock(ShopifyWebhookSubscriptionDiagnosticsService.class);
        ShopifyBridgeMerchantStoreService service = new ShopifyBridgeMerchantStoreService(
            client,
            properties(),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyBridgeSourcePreflightService.class),
            mock(ShopifyBridgeStoreSyncService.class),
            mock(ShopifyStorefrontPreviewService.class),
            mock(ShopifyBridgeUsageService.class),
            mock(ShopifyBridgeBillingService.class),
            diagnosticsService,
            mock(ShopifyStorefrontGovernedActionService.class),
            mock(ShopifyBridgeSupportReadinessService.class)
        );
        when(diagnosticsService.forShop("alpha.myshopify.com")).thenReturn(new ShopifyWebhookSubscriptionStatusSummary(
            "alpha.myshopify.com",
            "READY",
            "All required Shopify webhook subscriptions are present.",
            "https://bridge.example.com/api/webhooks/shopify",
            12,
            12,
            0,
            0,
            Instant.parse("2026-04-18T12:05:00Z"),
            List.of()
        ));

        ShopifyWebhookSubscriptionStatusSummary summary = service.webhookSubscriptions(session());

        assertThat(summary.status()).isEqualTo("READY");
        verify(diagnosticsService).forShop("alpha.myshopify.com");
    }

    private ShopifyMerchantSession session() {
        return new ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z")
        );
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private ShopifyBridgeStoreVectorizationSummary vectorization(String shopDomain) {
        return new ShopifyBridgeStoreVectorizationSummary(
            shopDomain,
            "dep-1",
            true,
            List.of("products", "collections"),
            List.of("product"),
            List.of("mkp-action-shopify-companion-read", "mkp-inference-shared-embeddings", "mkp-data-shopify-catalog"),
            List.of("mkp-action-shopify-companion-read", "mkp-inference-shared-embeddings", "mkp-data-shopify-catalog"),
            List.of(),
            List.of(),
            false,
            true,
            "vcn-1",
            "READY",
            "SHOPIFY-STORE",
            true,
            "vpl-1",
            "ACTIVE",
            true,
            "vrr-1",
            "ACTIVE",
            false,
            "APPLIED_VERIFIED",
            "PLATFORM_MANAGED_AUTO",
            "IN_SYNC",
            true,
            List.of(),
            null,
            new ShopifyBridgeStoreVectorizationPolicySummary(
                1,
                false,
                List.of(new ShopifyBridgeStoreVectorizationSourcePolicySummary(
                    "products",
                    true,
                    true,
                    true,
                    false,
                    true,
                    true,
                    "INDEXED_FIELDS_ONLY",
                    List.of(),
                    30,
                    60
                )),
                "system",
                Instant.parse("2026-04-18T12:00:00Z")
            ),
            List.of(new ShopifyBridgeStoreVectorizationIndexedFieldSummary(
                "products.title",
                "products",
                "product",
                "title",
                "Product title",
                true
            )),
            new ShopifyBridgeStoreVectorizationAutomationSummary(
                true,
                0,
                0,
                0,
                0,
                0,
                0,
                Instant.parse("2026-04-18T12:00:00Z"),
                Instant.parse("2026-04-18T12:00:00Z"),
                null,
                "vrn-1",
                List.of()
            ),
            List.of(new ShopifyBridgeStoreVectorizationEventSummary(
                "evt-1",
                "products",
                "product",
                "gid://shopify/Product/1",
                "products/update",
                "UPDATE",
                "COMPLETED",
                "AUTO_INDEX",
                null,
                "vrn-1",
                "wh-1",
                Instant.parse("2026-04-18T12:00:00Z"),
                Instant.parse("2026-04-18T12:00:01Z"),
                Instant.parse("2026-04-18T12:00:02Z"),
                Instant.parse("2026-04-18T12:00:03Z"),
                "Completed"
            ))
        );
    }

    private ShopifyBridgeStoreSummary store(String shopDomain) {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            shopDomain,
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
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
            "CONNECTED",
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
            new ShopifyBridgeStoreCapabilitySummary(
                2,
                2,
                2,
                1,
                List.of("list_products", "get_policy"),
                List.of("shopify-catalog", "shopify-policies"),
                List.of("search", "support"),
                List.of("shopify-products")
            ),
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

    private ShopifyBridgeStoreSummary uninstalledStore(String shopDomain) {
        ShopifyBridgeStoreSummary base = store(shopDomain);
        return new ShopifyBridgeStoreSummary(
            base.id(),
            base.shopDomain(),
            base.displayName(),
            base.productServiceRef(),
            base.productServiceDisplayName(),
            base.customerId(),
            base.customerName(),
            base.deploymentId(),
            base.deploymentName(),
            base.deploymentStatus(),
            base.consumerId(),
            base.consumerDisplayName(),
            "UNINSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "BLOCKED",
            base.productsEnabled(),
            base.collectionsEnabled(),
            base.pagesEnabled(),
            base.policiesEnabled(),
            base.articlesEnabled(),
            base.metaobjectsEnabled(),
            null,
            base.sourcePreflight(),
            base.syncDetail(),
            base.webhookDetail(),
            base.widgetDetail(),
            base.capabilities(),
            base.readiness(),
            base.latestVersion(),
            base.latestRelease(),
            base.lastSourcePreflightAt(),
            base.lastSyncAt(),
            base.lastWebhookAt(),
            base.createdAt(),
            base.updatedAt()
        );
    }

    private ShopifyBridgeCredentialAcquisition acquisition(ShopifyBridgeStoreSummary store) {
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

    private HttpClientErrorException notFound() {
        return HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
    }
}
