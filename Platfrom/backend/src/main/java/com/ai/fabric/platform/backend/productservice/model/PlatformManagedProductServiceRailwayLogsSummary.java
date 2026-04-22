package com.ai.fabric.platform.backend.productservice.model;

import com.ai.fabric.platform.backend.deployment.model.RailwayLogEntrySummary;

import java.time.Instant;
import java.util.List;

public record PlatformManagedProductServiceRailwayLogsSummary(
    String serviceRef,
    String source,
    boolean available,
    String message,
    String railwayProjectId,
    String railwayEnvironmentId,
    String railwayServiceId,
    String railwayDeploymentId,
    int requestedLimit,
    String filter,
    String startDate,
    String endDate,
    Instant queriedAt,
    List<RailwayLogEntrySummary> entries
) {
}
