package com.ai.fabric.product.shopify.bridge.diagnostics.service;

import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeInstallOverview;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeStoreOverview;
import com.ai.fabric.product.shopify.bridge.install.repository.ShopifyInstallRecordRepository;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ShopifyBridgeDiagnosticsService {

    private final ShopifyBridgeProperties properties;
    private final ShopifyInstallRecordRepository installRecordRepository;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeUsageService usageService;
    private final Instant serverStartedAt;

    public ShopifyBridgeDiagnosticsService(ShopifyBridgeProperties properties,
                                          ShopifyInstallRecordRepository installRecordRepository,
                                          PlatformShopifyStoreClient platformShopifyStoreClient,
                                          ShopifyBridgeBillingService billingService,
                                          ShopifyBridgeUsageService usageService) {
        this.properties = properties;
        this.installRecordRepository = installRecordRepository;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.billingService = billingService;
        this.usageService = usageService;
        this.serverStartedAt = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    public ShopifyBridgeOverviewResponse overview() {
        var installRecords = installRecordRepository.findAll();
        ShopifyBridgeInstallOverview installs = new ShopifyBridgeInstallOverview(
            installRecords.size(),
            (int) installRecords.stream().filter(record -> "INSTALLED".equalsIgnoreCase(record.getStatus())).count(),
            (int) installRecords.stream().filter(record -> "UNINSTALLED".equalsIgnoreCase(record.getStatus())).count(),
            (int) installRecords.stream().filter(record -> hasText(record.getAccessTokenSecretRef())).count(),
            installRecords.stream()
                .map(record -> record.getLastAuthenticatedAt())
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null),
            installRecords.stream()
                .map(record -> record.getLastUninstalledAt())
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null)
        );

        ShopifyBridgeStoreOverview stores = buildStoreOverview();
        ShopifyBridgeBillingSummary billing = billingService.summarize();
        ShopifyBridgeUsageOverview usage = usageService.summarizeAllShops();
        String status = !properties.adminApiKey().isBlank() && "READY".equalsIgnoreCase(stores.platformAccessStatus())
            ? "READY"
            : "DEGRADED";

        return new ShopifyBridgeOverviewResponse(
            properties.appName(),
            properties.serviceRef(),
            properties.productFamily(),
            properties.serviceKind(),
            properties.environmentScope(),
            emptyToNull(properties.platformBaseUrl()),
            emptyToNull(properties.publicBaseUrl()),
            !properties.adminApiKey().isBlank(),
            status,
            serverStartedAt,
            installs,
            stores,
            billing,
            usage,
            List.of(
                "managed-service-health",
                "platform-bridge-auth",
                "shopify-auth-shell",
                "shopify-oauth-install-flow",
                "merchant-admin-ui",
                "source-preflight-jobs",
                "platform-bootstrap-client",
                "shopify-webhook-ingestion",
                "sync-shell",
                "storefront-bridge-shell",
                "billing-posture-summary",
                "usage-attribution-summary"
            ),
            List.of()
        );
    }

    private ShopifyBridgeStoreOverview buildStoreOverview() {
        try {
            List<ShopifyBridgeStoreSummary> stores = platformShopifyStoreClient.listStores();
            return new ShopifyBridgeStoreOverview(
                "READY",
                "Platform store mappings resolved successfully.",
                stores.size(),
                (int) stores.stream().filter(store -> store.readiness() != null && store.readiness().goLiveEligible()).count(),
                (int) stores.stream().filter(store -> store.readiness() != null && store.readiness().storefrontReady()).count(),
                (int) stores.stream().filter(store -> "LIVE".equalsIgnoreCase(store.onboardingStatus())).count(),
                (int) stores.stream().filter(store -> "BLOCKED".equalsIgnoreCase(store.onboardingStatus())).count(),
                stores.stream()
                    .map(ShopifyBridgeStoreSummary::lastWebhookAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null)
            );
        } catch (RuntimeException ex) {
            return new ShopifyBridgeStoreOverview(
                "FAILED",
                Optional.ofNullable(ex.getMessage()).filter(message -> !message.isBlank()).orElse("Failed to resolve platform store mappings."),
                0,
                0,
                0,
                0,
                0,
                null
            );
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
