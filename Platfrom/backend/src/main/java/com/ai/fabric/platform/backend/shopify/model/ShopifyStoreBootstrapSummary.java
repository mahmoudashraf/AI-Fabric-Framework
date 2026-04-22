package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreBootstrapSummary(
    String shopDomain,
    String customerId,
    String deploymentId,
    String consumerId,
    boolean createdCustomer,
    boolean createdDeployment,
    boolean createdConsumer,
    List<String> installedPluginIds,
    ShopifyStoreConnectionSummary store
) {
}
