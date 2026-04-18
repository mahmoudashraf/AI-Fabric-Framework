package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ShopifyBridgeStoreLifecycleService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;

    public ShopifyBridgeStoreLifecycleService(PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public ShopifyBridgeStoreSummary markUninstalled(String shopDomain) {
        try {
            ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
            return platformShopifyStoreClient.upsertStore(new ShopifyBridgeUpsertStoreRequest(
                store.shopDomain(),
                store.displayName(),
                store.productServiceRef(),
                store.customerId(),
                store.deploymentId(),
                store.consumerId(),
                "UNINSTALLED",
                "NOT_SYNCED",
                store.sourceReadinessStatus(),
                "NOT_ENABLED",
                "BLOCKED",
                store.productsEnabled(),
                store.collectionsEnabled(),
                store.pagesEnabled(),
                store.policiesEnabled()
            ));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            throw ex;
        }
    }
}
