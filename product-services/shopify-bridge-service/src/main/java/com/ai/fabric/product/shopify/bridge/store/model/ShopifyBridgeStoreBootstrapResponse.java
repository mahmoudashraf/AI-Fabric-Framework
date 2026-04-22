package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreBootstrapResponse(
    String shopDomain,
    String customerId,
    String deploymentId,
    String consumerId,
    boolean createdCustomer,
    boolean createdDeployment,
    boolean createdConsumer,
    List<String> installedPluginIds,
    ShopifyBridgeStoreSummary store
) {
}
