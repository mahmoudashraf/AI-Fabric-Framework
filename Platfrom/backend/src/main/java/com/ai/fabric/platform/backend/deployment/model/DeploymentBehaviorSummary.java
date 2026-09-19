package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record DeploymentBehaviorSummary(
    String code,
    String name,
    String description,
    String schemaVersion,
    int contractVersion,
    String maturity,
    boolean authoringEnabled,
    boolean releaseRequiresCapabilityManifest,
    String availabilityMessage,
    List<String> activationSources,
    List<String> channelBindings,
    List<String> allowedExecutionExtensions,
    List<String> requiredRuntimeCapabilities,
    List<String> requiredRuntimeEndpointClasses,
    List<String> requiredRuntimeMigrationIds,
    List<String> baselineVerificationPackIds,
    List<DeploymentSpecialistBundleSummary> requiredSpecialistBundles,
    JsonNode defaultConfig
) {
}
