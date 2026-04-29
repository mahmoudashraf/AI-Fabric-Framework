package com.ai.fabric.platform.backend.shopify.model;

public record UpsertShopifyCompanionPackageProfileRequest(
    String packageKey,
    String tierKey,
    String runtimeProfileKey,
    String vectorProfileKey,
    String displayName,
    String description,
    String costPosture,
    String templatePluginId,
    String templatePluginVersion,
    String deploymentTemplateId,
    String inferencePluginId,
    String vectorStrategy,
    String vectorProvisioningMode,
    String vectorStoragePosture,
    String verificationPackId,
    String status,
    String detailsJson,
    String reason
) {
}
