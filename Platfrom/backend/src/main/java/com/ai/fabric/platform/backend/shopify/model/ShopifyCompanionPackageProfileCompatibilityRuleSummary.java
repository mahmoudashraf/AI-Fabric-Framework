package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyCompanionPackageProfileCompatibilityRuleSummary(
    String packageKey,
    String tierKey,
    String costPosture,
    String defaultProfileKey,
    String defaultRuntimeProfileKey,
    String defaultVectorProfileKey,
    String defaultInferencePluginId,
    String defaultVectorStrategy,
    String defaultVectorProvisioningMode,
    String defaultVectorStoragePosture,
    String defaultDeploymentTemplateId,
    String defaultVerificationPackId,
    List<String> allowedRuntimeProfileKeys,
    List<String> allowedVectorProfileKeys,
    List<String> allowedInferencePluginIds,
    List<String> allowedVectorStrategies,
    List<String> allowedVectorProvisioningModes,
    List<String> allowedVectorStoragePostures,
    List<String> allowedDeploymentTemplateIds,
    List<String> allowedVerificationPackIds
) {
}
