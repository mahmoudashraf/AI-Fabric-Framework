package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayPreflightSummary(
    String mode,
    boolean ready,
    String checkedAt,
    String publicBaseUrl,
    String workspaceId,
    String workspaceName,
    String repository,
    String branch,
    List<RailwayPreflightCheckSummary> checks
) {
}
