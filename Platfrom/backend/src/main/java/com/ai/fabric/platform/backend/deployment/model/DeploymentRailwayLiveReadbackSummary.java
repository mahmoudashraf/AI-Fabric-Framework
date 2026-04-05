package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentRailwayLiveReadbackSummary(
    boolean available,
    String status,
    String summaryMessage,
    String projectId,
    String projectName,
    String environmentId,
    String environmentName,
    DeploymentRailwayLiveServiceSummary runtime,
    DeploymentRailwayLiveServiceSummary restConnector,
    DeploymentRailwayLiveServiceSummary vectorizationRunner
) {
}
