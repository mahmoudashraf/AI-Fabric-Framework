package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record RailwayServicePlanSummary(
    String serviceName,
    String rootDir,
    String baseUrl,
    List<RailwayEnvVarSummary> env
) {
}
