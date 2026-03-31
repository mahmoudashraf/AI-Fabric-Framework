package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentConfigTemplateSourceSummary(
    String templateId,
    String templateName,
    String templateDescription,
    String llmProvider,
    String embeddingProvider,
    String vectorStrategy,
    String runtimeProfile,
    String connectorProfile,
    String repository,
    String branch,
    String repositoryOverride,
    String branchOverride,
    boolean overrideActive
) {
}
