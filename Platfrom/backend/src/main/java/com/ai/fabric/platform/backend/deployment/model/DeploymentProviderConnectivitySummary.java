package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentProviderConnectivitySummary(
    String deploymentId,
    String deploymentName,
    String llmProvider,
    String embeddingProvider,
    String vectorStrategy,
    List<DeploymentProviderConnectivityProbeSummary> probes,
    String summaryMessage
) {
}
