package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentProviderResourceOrphanScanSummary(
    String deploymentId,
    boolean marked,
    int candidateCount,
    List<DeploymentProviderResourceLifecycleSummary> candidates,
    String message
) {
}
