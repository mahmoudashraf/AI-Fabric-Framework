package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentVerificationRolloutSummary(
    String summaryMessage,
    List<DeploymentVerificationRolloutItemSummary> items
) {
}
