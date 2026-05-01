package com.ai.fabric.platform.backend.shopify.model;

import java.time.Instant;
import java.util.List;

public record ShopifyCompanionPackageProfileOptionsSummary(
    List<ShopifyCompanionPackageProfileChoiceSummary> profileKeys,
    List<ShopifyCompanionPackageProfileChoiceSummary> packages,
    List<ShopifyCompanionPackageProfileChoiceSummary> tiers,
    List<ShopifyCompanionPackageProfileChoiceSummary> statuses,
    List<ShopifyCompanionPackageProfileChoiceSummary> costPostures,
    List<ShopifyCompanionPackageProfileChoiceSummary> runtimeProfiles,
    List<ShopifyCompanionPackageProfileChoiceSummary> vectorProfiles,
    List<ShopifyCompanionPackageProfileChoiceSummary> templatePlugins,
    List<ShopifyCompanionPackageProfileChoiceSummary> templateVersions,
    List<ShopifyCompanionPackageProfileChoiceSummary> deploymentTemplates,
    List<ShopifyCompanionPackageProfileChoiceSummary> inferencePlugins,
    List<ShopifyCompanionPackageProfileChoiceSummary> vectorStrategies,
    List<ShopifyCompanionPackageProfileChoiceSummary> vectorProvisioningModes,
    List<ShopifyCompanionPackageProfileChoiceSummary> vectorStoragePostures,
    List<ShopifyCompanionPackageProfileChoiceSummary> verificationPacks,
    List<ShopifyCompanionPackageProfileBlueprintSummary> profileBlueprints,
    List<ShopifyCompanionPackageProfileCompatibilityRuleSummary> compatibilityRules,
    Instant generatedAt
) {
}
