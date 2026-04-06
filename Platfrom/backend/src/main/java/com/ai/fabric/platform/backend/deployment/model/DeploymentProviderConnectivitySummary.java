package com.ai.fabric.platform.backend.deployment.model;

import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;

import java.util.List;

public record DeploymentProviderConnectivitySummary(
    String deploymentId,
    String deploymentName,
    String llmProvider,
    String embeddingProvider,
    String vectorStrategy,
    String vectorProvisioningMode,
    boolean managedVectorProvisioningEnabled,
    String managedVectorProvisioningMode,
    List<String> managedVectorTargets,
    String managedVectorSummaryMessage,
    List<DeploymentProviderConnectivityProbeSummary> probes,
    String summaryMessage,
    List<DeploymentSecretResolutionSummary> effectiveSecretResolutions
) {
}
