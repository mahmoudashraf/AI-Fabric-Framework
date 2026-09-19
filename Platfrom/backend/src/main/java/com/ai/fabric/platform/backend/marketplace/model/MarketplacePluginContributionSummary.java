package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplacePluginContributionSummary(
    String templateCuratedModuleId,
    List<String> actionIds,
    List<String> knowledgeSourceIds,
    List<String> shellModuleIds,
    List<String> shellCardIds,
    List<String> automationWorkflowIds,
    List<String> inferenceProfileIds,
    List<String> inferenceEndpointProfileRefs,
    List<String> inferenceManagedServiceRefs,
    String templateDeploymentBehaviorType,
    Integer templateDeploymentBehaviorContractVersion,
    List<String> templateRequiredRuntimeCapabilityIds,
    List<String> templateAllowedExecutionExtensions,
    List<String> templateAllowedChannelBindings,
    List<String> templateVerificationPackIds,
    List<String> templateRequiredPluginRefs,
    String specialistContractVersion,
    List<String> specialistCompatibleBehaviorTypes,
    List<MarketplaceSpecialistBundleRefSummary> specialistBundleRefs,
    List<String> specialistRequiredRuntimeCapabilityIds,
    List<String> specialistRequiredMigrationIds,
    List<String> specialistRequiredSecretNames,
    List<String> specialistVerificationPackIds,
    List<String> specialistUnsupportedClaims
) {
}
