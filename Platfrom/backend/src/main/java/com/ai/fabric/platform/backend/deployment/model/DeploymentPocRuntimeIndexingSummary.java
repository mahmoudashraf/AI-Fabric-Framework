package com.ai.fabric.platform.backend.deployment.model;

import java.util.Map;

public record DeploymentPocRuntimeIndexingSummary(
    boolean available,
    String vectorDb,
    Map<String, Long> countsByEntityType,
    long totalVectors,
    boolean supportsVectorScan
) {
}
