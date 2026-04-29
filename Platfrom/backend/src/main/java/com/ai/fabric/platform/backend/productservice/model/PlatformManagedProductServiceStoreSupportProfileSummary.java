package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceStoreSupportProfileSummary(
    String contactEmail,
    String contactUrl,
    String helpCenterUrl,
    String orderLookupPageUrl,
    String supportPolicyNote,
    boolean merchantHandoffConfigured
) {
}
