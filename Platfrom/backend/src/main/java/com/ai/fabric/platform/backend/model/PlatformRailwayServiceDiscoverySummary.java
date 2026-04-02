package com.ai.fabric.platform.backend.model;

public record PlatformRailwayServiceDiscoverySummary(
    boolean available,
    String summaryMessage,
    String publicHost,
    String projectId,
    String projectName,
    String environmentId,
    String environmentName,
    String serviceId,
    String serviceName,
    String domain,
    String latestDeploymentId,
    String latestDeploymentStatus,
    String latestDeploymentUrl,
    String latestDeploymentStaticUrl,
    String latestDeploymentCreatedAt,
    String rootDirectory,
    String dockerfilePath,
    String healthcheckPath,
    String upstreamUrl,
    String sourceRepo,
    String sourceImage,
    String triggerRepository,
    String triggerBranch
) {
}
