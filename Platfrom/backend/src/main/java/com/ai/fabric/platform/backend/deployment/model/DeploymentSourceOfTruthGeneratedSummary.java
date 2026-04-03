package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentSourceOfTruthGeneratedSummary(
    String provisioningMode,
    String artifactStrategy,
    String projectName,
    String repository,
    String branch,
    String runtimeServiceName,
    String runtimeDockerfilePath,
    String runtimeBaseUrl,
    String restConnectorServiceName,
    String restConnectorDockerfilePath,
    String connectorBaseUrl
) {
}
