package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentManagedVectorStateSummary(
    String status,
    boolean managedRequested,
    String vectorStrategy,
    String vectorProvisioningMode,
    boolean driftDetected,
    String driftStatus,
    int activeResourceCount,
    int detachedResourceCount,
    int driftedResourceCount,
    List<DeploymentManagedVectorResourceSummary> resources,
    String driftMessage,
    String summaryMessage
) {
}
