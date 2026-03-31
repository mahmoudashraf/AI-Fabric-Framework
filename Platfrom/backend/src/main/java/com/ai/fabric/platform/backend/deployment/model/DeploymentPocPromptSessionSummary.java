package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocPromptSessionSummary(
    String id,
    String deploymentId,
    String actorId,
    String actorDisplayName,
    String sessionLabel,
    boolean active,
    int promptKeyCount,
    List<String> promptKeys,
    String updatedAt
) {
}
