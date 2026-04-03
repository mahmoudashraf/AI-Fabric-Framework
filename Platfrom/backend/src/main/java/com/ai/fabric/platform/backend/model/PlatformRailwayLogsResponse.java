package com.ai.fabric.platform.backend.model;

import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;

import java.time.Instant;
import java.util.List;

public record PlatformRailwayLogsResponse(
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
