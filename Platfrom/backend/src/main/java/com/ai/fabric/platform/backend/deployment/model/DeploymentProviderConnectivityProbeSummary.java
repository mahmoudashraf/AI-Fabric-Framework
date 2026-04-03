package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentProviderConnectivityProbeSummary(
    String key,
    String label,
    String status,
    String endpoint,
    String message
) {
}
