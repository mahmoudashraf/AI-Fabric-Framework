package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyDeploymentSummary(
    String deploymentUuid,
    String applicationName,
    String applicationUuid,
    String status,
    String commit,
    String commitMessage,
    String createdAt,
    String updatedAt,
    String finishedAt,
    JsonNode raw
) {
}
