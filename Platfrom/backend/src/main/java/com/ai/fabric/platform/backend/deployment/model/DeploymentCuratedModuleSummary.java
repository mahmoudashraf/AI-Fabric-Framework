package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentCuratedModuleSummary(
    String id,
    String name,
    String description,
    String runtimeCuratedPack,
    String promptPresetId
) {
}
