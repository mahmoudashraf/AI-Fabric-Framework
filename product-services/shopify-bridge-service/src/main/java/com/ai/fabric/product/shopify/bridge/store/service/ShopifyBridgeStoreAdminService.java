package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSummary;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordBillingStateRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourcePageResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyBridgeStoreAdminService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeSourcePreflightService sourcePreflightService;
    private final ShopifyBridgeStoreSyncService storeSyncService;
    private final ShopifyBridgeVectorizationSourceService vectorizationSourceService;
    private final ShopifyBridgeUsageService usageService;
    private final ShopifyStorefrontGovernedActionService governedActionService;
    private final ShopifyBridgeSupportReadinessService supportReadinessService;

    public ShopifyBridgeStoreAdminService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                          ShopifyBridgeInstallCredentialService installCredentialService,
                                          ShopifyInstallRecordService installRecordService,
                                          ShopifyBridgeBillingService billingService,
                                          ShopifyBridgeSourcePreflightService sourcePreflightService,
                                          ShopifyBridgeStoreSyncService storeSyncService,
                                          ShopifyBridgeVectorizationSourceService vectorizationSourceService,
                                          ShopifyBridgeUsageService usageService,
                                          ShopifyStorefrontGovernedActionService governedActionService,
                                          ShopifyBridgeSupportReadinessService supportReadinessService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.installRecordService = installRecordService;
        this.billingService = billingService;
        this.sourcePreflightService = sourcePreflightService;
        this.storeSyncService = storeSyncService;
        this.vectorizationSourceService = vectorizationSourceService;
        this.usageService = usageService;
        this.governedActionService = governedActionService;
        this.supportReadinessService = supportReadinessService;
    }

    public List<ShopifyBridgeStoreSummary> listStores() {
        return platformShopifyStoreClient.listStores();
    }

    public ShopifyBridgeStoreSummary getStore(String shopDomain) {
        return platformShopifyStoreClient.getStore(shopDomain);
    }

    public ShopifyBridgeBillingSummary billingSummary(String shopDomain) {
        return installCredentialService.resolvePersistedMaterial(shopDomain)
            .map(acquisition -> billingService.summarizeForShop(shopDomain, acquisition.tokenExchangeMaterial().accessToken()))
            .orElseGet(() -> billingService.summarizeForShop(shopDomain, null));
    }

    public ShopifyBridgeBillingSummary recordBillingState(String shopDomain,
                                                          ShopifyBridgeRecordBillingStateRequest request) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (store == null || store.shopDomain() == null || store.shopDomain().isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Shopify store must be platform-registered before billing state can be recorded.");
        }
        if ("UNINSTALLED".equalsIgnoreCase(store.installStatus())) {
            throw new ResponseStatusException(CONFLICT, "Billing state cannot be recorded for an uninstalled Shopify store.");
        }
        String tierKey = normalizeTierKey(request == null ? null : request.tierKey());
        String status = normalizeBillingStatus(request == null ? null : request.status());
        List<ShopifyBridgeSupportSubscriptionSummary> subscriptions = buildRecordedSubscriptions(
            tierKey,
            status,
            request
        );
        platformShopifyStoreClient.recordBillingState(store.shopDomain(), request);
        installRecordService.recordBillingState(
            store.shopDomain(),
            tierKey,
            status,
            subscriptions
        );
        return billingSummary(store.shopDomain());
    }

    public ShopifyBridgeUsageSummary usageSummary(String shopDomain) {
        return usageService.summarize(shopDomain);
    }

    public ShopifyBridgeSupportReadinessSummary supportReadiness(String shopDomain) {
        return supportReadinessService.summarizeForShop(shopDomain);
    }

    public List<ShopifyBridgeGovernedActionAuditSummary> recentGovernedActions(String shopDomain, int limit) {
        return governedActionService.recentActions(shopDomain, limit);
    }

    public ShopifyBridgeStoreVectorizationSummary vectorizationSummary(String shopDomain) {
        return platformShopifyStoreClient.getVectorization(shopDomain);
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(String shopDomain) {
        return platformShopifyStoreClient.bootstrap(shopDomain);
    }

    public ShopifyBridgeStoreSummary runSourcePreflight(String shopDomain) {
        return installCredentialService.resolvePersistedMaterial(shopDomain)
            .map(sourcePreflightService::run)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify source preflight requires persisted store credentials. Install or reconnect the app first."
            ));
    }

    public ShopifyBridgeStoreSummary runSync(String shopDomain) {
        return installCredentialService.resolvePersistedMaterial(shopDomain)
            .map(storeSyncService::sync)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify sync requires persisted store credentials. Install or reconnect the app first."
            ));
    }

    public ShopifyBridgeStoreSummary recordSourcePreflight(String shopDomain,
                                                           ShopifyBridgeRecordSourcePreflightRequest request) {
        return platformShopifyStoreClient.recordSourcePreflight(shopDomain, request);
    }

    public ShopifyBridgeStoreSummary recordSyncStatus(String shopDomain,
                                                      ShopifyBridgeRecordSyncStatusRequest request) {
        return platformShopifyStoreClient.recordSyncStatus(shopDomain, request);
    }

    public ShopifyBridgeStoreSummary recordWidgetStatus(String shopDomain,
                                                        ShopifyBridgeRecordWidgetStatusRequest request) {
        return platformShopifyStoreClient.recordWidgetStatus(shopDomain, request);
    }

    public ShopifyBridgeVectorizationSourcePageResponse vectorizationSourcePage(String shopDomain,
                                                                                String entityType,
                                                                                String cursor,
                                                                                Integer limit) {
        return vectorizationSourceService.page(shopDomain, entityType, cursor, limit);
    }

    public com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourceRecord vectorizationSourceRecord(String shopDomain,
                                                                                                                             String sourceCategory,
                                                                                                                             String sourceObjectId) {
        return vectorizationSourceService.record(shopDomain, sourceCategory, sourceObjectId);
    }

    private String normalizeTierKey(String value) {
        String tierKey = value == null || value.isBlank() ? "FREE" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FREE", "STARTER", "ELITE").contains(tierKey)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify Companion billing tier: " + value);
        }
        return tierKey;
    }

    private String normalizeBillingStatus(String value) {
        String status = value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "PAYMENT_ISSUE", "CHECK_FAILED").contains(status)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported Shopify Companion billing status: " + value);
        }
        return status;
    }

    private List<ShopifyBridgeSupportSubscriptionSummary> buildRecordedSubscriptions(String tierKey,
                                                                                     String status,
                                                                                     ShopifyBridgeRecordBillingStateRequest request) {
        if ("FREE".equalsIgnoreCase(tierKey)) {
            return List.of();
        }
        String subscriptionId = request == null || request.subscriptionId() == null || request.subscriptionId().isBlank()
            ? "recorded-shopify-billing-" + tierKey.toLowerCase(Locale.ROOT)
            : request.subscriptionId().trim();
        String subscriptionName = request == null || request.subscriptionName() == null || request.subscriptionName().isBlank()
            ? "Loom Companion " + tierKey.substring(0, 1) + tierKey.substring(1).toLowerCase(Locale.ROOT)
            : request.subscriptionName().trim();
        return List.of(new ShopifyBridgeSupportSubscriptionSummary(
            subscriptionId,
            subscriptionName,
            status,
            tierKey,
            "ACTIVE".equalsIgnoreCase(status)
        ));
    }
}
