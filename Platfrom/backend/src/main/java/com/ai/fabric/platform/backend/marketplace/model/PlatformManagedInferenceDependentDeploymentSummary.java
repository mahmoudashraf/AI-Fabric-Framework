package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record PlatformManagedInferenceDependentDeploymentSummary(
    String deploymentId,
    String deploymentName,
    String environmentName,
    String deploymentStatus,
    String activeVersionId,
    boolean currentDraftUsesService,
    boolean activeVersionUsesService,
    boolean runtimeActive,
    List<String> usages
) {
}
