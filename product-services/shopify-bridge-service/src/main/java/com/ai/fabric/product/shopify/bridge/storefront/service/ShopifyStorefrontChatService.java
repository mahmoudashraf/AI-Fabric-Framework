package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShopifyStorefrontChatService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;

    public ShopifyStorefrontChatService(PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public JsonNode query(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        return platformShopifyStoreClient.queryConsumerBridgeChat(store.consumerId(), request, shopperSessionId);
    }

    public JsonNode suggestions(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        return platformShopifyStoreClient.suggestConsumerBridgeChat(store.consumerId(), request, shopperSessionId);
    }

    private ShopifyBridgeStoreSummary requireReadyStore(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            throw unavailable("Store assistant is not installed for " + store.shopDomain() + ".");
        }
        if (!"READY".equalsIgnoreCase(store.sourceReadinessStatus())) {
            throw unavailable("Store data is not ready yet for " + store.shopDomain() + ". Complete source preflight and apply-time sync first.");
        }
        if (!StringUtils.hasText(store.consumerId()) || !StringUtils.hasText(store.deploymentId())) {
            throw unavailable("Store assistant mapping is incomplete for " + store.shopDomain() + ".");
        }
        return store;
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
