package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record BootstrapShopifyStoreRequest(
    String customerName,
    String deploymentName,
    String environment,
    String consumerId,
    String templatePluginId,
    String templatePluginVersion,
    List<String> pluginIds
) {
}
