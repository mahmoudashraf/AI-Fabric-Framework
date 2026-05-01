package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeCreateProvisioningJobRequest(
    String jobType,
    String requestedPackageKey,
    String requestedTierKey,
    String requestedRuntimeProfileKey,
    String requestedVectorProfileKey,
    String requestedTemplatePluginId,
    String requestedTemplatePluginVersion,
    List<String> requestedPluginIds,
    String installIntentId,
    String reason,
    Boolean processImmediately
) {
}
