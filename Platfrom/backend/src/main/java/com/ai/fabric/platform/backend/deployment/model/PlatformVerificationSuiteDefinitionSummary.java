package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record PlatformVerificationSuiteDefinitionSummary(
    String key,
    String label,
    String description,
    boolean releaseBlocking,
    List<PlatformVerificationSuiteStageDefinitionSummary> stages
) {
}
