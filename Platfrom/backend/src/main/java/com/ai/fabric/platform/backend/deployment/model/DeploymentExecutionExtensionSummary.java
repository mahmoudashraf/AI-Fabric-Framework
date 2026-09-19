package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentExecutionExtensionSummary(
    String code,
    String name,
    String description,
    String maturity,
    String availabilityMessage,
    List<String> compatibleBehaviorTypes,
    List<String> requiredRuntimeCapabilities,
    List<String> requiredRuntimeEndpointClasses,
    List<String> requiredRuntimeMigrationIds,
    List<String> verificationPackIds
) {
}
