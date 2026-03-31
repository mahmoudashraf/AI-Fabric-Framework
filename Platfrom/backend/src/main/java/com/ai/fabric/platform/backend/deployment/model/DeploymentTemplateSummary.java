package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentTemplateSummary(
    String id,
    String name,
    String description,
    String llmProvider,
    String embeddingProvider,
    String vectorStrategy,
    String runtimeProfile,
    String connectorProfile,
    boolean managedVectorProvisioningDefault,
    String managedVectorProvisioningMode,
    String managedVectorProvisioningSummary
) {
}
