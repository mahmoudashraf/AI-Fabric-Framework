package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentRailwayLiveServiceSummary(
    String key,
    String label,
    String serviceId,
    String status,
    String summaryMessage,
    DeploymentRailwayLiveFieldDriftSummary rootDirectory,
    DeploymentRailwayLiveFieldDriftSummary dockerfilePath,
    DeploymentRailwayLiveFieldDriftSummary repository,
    DeploymentRailwayLiveFieldDriftSummary branch,
    DeploymentRailwayLiveFieldDriftSummary publicBaseUrl,
    int expectedEnvCount,
    int matchingEnvCount,
    int missingEnvCount,
    int mismatchedEnvCount,
    List<DeploymentRailwayLiveEnvVarDriftSummary> envVars
) {
}
