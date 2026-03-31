package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentServiceConfigSummary(
    String key,
    String label,
    String purpose,
    String status,
    String baseUrl,
    int requiredFieldCount,
    int configuredRequiredFieldCount,
    List<DeploymentServiceConfigFieldSummary> fields,
    List<DeploymentServiceConfigIssueSummary> issues,
    String summaryMessage
) {
}
