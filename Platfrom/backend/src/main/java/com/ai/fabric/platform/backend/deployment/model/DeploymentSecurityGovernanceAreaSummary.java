package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentSecurityGovernanceAreaSummary(
    String key,
    String label,
    String status,
    int blockedCount,
    int warningCount,
    List<DeploymentSecurityGovernanceCheckSummary> checks,
    String summaryMessage
) {
}
