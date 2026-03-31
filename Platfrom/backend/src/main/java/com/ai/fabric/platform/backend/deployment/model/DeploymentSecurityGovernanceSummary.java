package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentSecurityGovernanceSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    List<DeploymentSecurityGovernanceAreaSummary> areas,
    int blockedCount,
    int warningCount,
    String summaryMessage
) {
}
