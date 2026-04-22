package com.ai.fabric.platform.backend.productservice.model;

import java.time.Instant;
import java.util.List;

public record PlatformManagedProductServiceDeploymentHistorySummary(
    String serviceRef,
    boolean available,
    String message,
    String railwayProjectId,
    String railwayEnvironmentId,
    String railwayServiceId,
    Instant generatedAt,
    List<PlatformManagedProductServiceRailwayDeploymentSummary> deployments
) {
}
