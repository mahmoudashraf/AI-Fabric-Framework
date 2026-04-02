package com.ai.fabric.platform.backend.model;

import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;

import java.util.List;

public record PlatformDiagnosticsSummary(
    String name,
    String stage,
    String currentPhase,
    String publicBaseUrl,
    String provisioningMode,
    String workspaceId,
    String repository,
    String branch,
    String summaryMessage,
    RailwayPreflightSummary railwayPreflight,
    String railwayPreflightError,
    PlatformRailwayServiceDiscoverySummary railwayService,
    List<DeploymentHostedVerificationRunSummary> recentHostedVerificationRuns
) {
}
