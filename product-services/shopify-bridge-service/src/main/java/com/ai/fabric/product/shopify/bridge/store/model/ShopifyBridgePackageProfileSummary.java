package com.ai.fabric.product.shopify.bridge.store.model;

public record ShopifyBridgePackageProfileSummary(
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
