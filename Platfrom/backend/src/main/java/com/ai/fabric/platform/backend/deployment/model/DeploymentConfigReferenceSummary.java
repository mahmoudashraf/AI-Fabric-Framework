package com.ai.fabric.platform.backend.deployment.model;

import java.time.Instant;

public record DeploymentConfigReferenceSummary(
    String stage,
    String referenceId,
    String referenceLabel,
    String configHash,
    Instant updatedAt,
    boolean available
) {
}
