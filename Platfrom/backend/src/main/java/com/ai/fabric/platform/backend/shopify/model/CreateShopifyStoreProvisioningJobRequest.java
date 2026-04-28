package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record CreateShopifyStoreProvisioningJobRequest(
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
