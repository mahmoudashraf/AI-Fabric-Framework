package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopifyBridgeStoreAdminService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;

    public ShopifyBridgeStoreAdminService(PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public List<ShopifyBridgeStoreSummary> listStores() {
        return platformShopifyStoreClient.listStores();
    }

    public ShopifyBridgeStoreSummary getStore(String shopDomain) {
        return platformShopifyStoreClient.getStore(shopDomain);
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(String shopDomain) {
        return platformShopifyStoreClient.bootstrap(shopDomain);
    }
}
