package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyCompanionPackageProfileSummary(
    String profileKey,
    String packageKey,
    String tierKey,
    String runtimeProfileKey,
    String vectorProfileKey,
    String displayName,
    String description,
    String costPosture,
    String vectorStrategy,
    String vectorProvisioningMode,
    String vectorStoragePosture,
    String verificationPackId,
    String status
) {
}
