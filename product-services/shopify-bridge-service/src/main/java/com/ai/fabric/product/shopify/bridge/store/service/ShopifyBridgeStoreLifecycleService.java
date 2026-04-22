package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWebhookEventRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
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
            return platformShopifyStoreClient.markUninstalled(shopDomain);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            throw ex;
        }
    }

    public void deleteStoreMapping(String shopDomain, boolean force) {
        try {
            platformShopifyStoreClient.deleteStore(shopDomain, force);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw ex;
            }
        }
    }

    public ShopifyBridgeStoreSummary recordWebhookEvent(String shopDomain,
                                                        String topic,
                                                        String eventType,
                                                        String sourceCategory,
                                                        String message,
                                                        boolean invalidateSync) {
        return platformShopifyStoreClient.recordWebhookEvent(
            shopDomain,
            new ShopifyBridgeRecordWebhookEventRequest(topic, eventType, sourceCategory, message, invalidateSync)
        );
    }
}
