package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;
import java.util.List;

public record DeploymentRailwayLogsResponse(
    String deploymentId,
    String releaseId,
    String deploymentVersionId,
    String releaseStatus,
    String provisioningTarget,
    String service,
    String source,
    boolean available,
    String message,
    String projectId,
    String environmentId,
    String serviceId,
    String serviceName,
    String railwayDeploymentId,
    int requestedLimit,
    String filter,
    String startDate,
    String endDate,
    Instant queriedAt,
    List<RailwayLogEntrySummary> entries
) {
}
