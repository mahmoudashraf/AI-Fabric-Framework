package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyBridgeStoreAdminService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeSourcePreflightService sourcePreflightService;

    public ShopifyBridgeStoreAdminService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                          ShopifyBridgeInstallCredentialService installCredentialService,
                                          ShopifyBridgeBillingService billingService,
                                          ShopifyBridgeSourcePreflightService sourcePreflightService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.billingService = billingService;
        this.sourcePreflightService = sourcePreflightService;
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
}
