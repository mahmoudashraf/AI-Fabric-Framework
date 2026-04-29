package com.ai.fabric.platform.backend.shopify.model;

public record ShopifyCompanionPackageProfileBlueprintSummary(
    String key,
    String label,
    String description,
    String profileKey,
    String packageKey,
    String tierKey,
    String runtimeProfileKey,
    String vectorProfileKey,
    String displayName,
    String costPosture,
    String templatePluginId,
    String templatePluginVersion,
    String deploymentTemplateId,
    String inferencePluginId,
    String vectorStrategy,
    String vectorProvisioningMode,
    String vectorStoragePosture,
    String verificationPackId
) {
}
