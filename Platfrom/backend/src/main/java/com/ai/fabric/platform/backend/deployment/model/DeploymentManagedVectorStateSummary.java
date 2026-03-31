package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentManagedVectorStateSummary(
    String status,
    boolean managedRequested,
    String vectorStrategy,
    String vectorProvisioningMode,
    int activeResourceCount,
    int detachedResourceCount,
    List<DeploymentManagedVectorResourceSummary> resources,
    String summaryMessage
) {
}
