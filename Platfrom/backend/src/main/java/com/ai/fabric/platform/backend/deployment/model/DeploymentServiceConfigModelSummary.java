package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentServiceConfigModelSummary(
    String deploymentId,
    String deploymentName,
    String environment,
    List<DeploymentServiceConfigSummary> services,
    String summaryMessage
) {
}
