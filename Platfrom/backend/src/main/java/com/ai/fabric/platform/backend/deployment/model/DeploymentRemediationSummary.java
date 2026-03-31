package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentRemediationSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    List<DeploymentRemediationActionSummary> actions,
    String summaryMessage
) {
}
