package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentNavigationSurfaceSummary(
    String key,
    String label,
    String purpose,
    String serviceName,
    String rootDir,
    String dockerfilePath,
    String primaryUrl,
    String docsUrl,
    String openApiUrl,
    String adminUrl,
    boolean available,
    String summaryMessage
) {
}
