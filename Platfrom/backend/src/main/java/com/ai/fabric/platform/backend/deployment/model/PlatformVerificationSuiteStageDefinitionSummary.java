package com.ai.fabric.platform.backend.deployment.model;

public record PlatformVerificationSuiteStageDefinitionSummary(
    String key,
    String label,
    String stageType,
    String targetRef,
    boolean blocking,
    String description
) {
}
