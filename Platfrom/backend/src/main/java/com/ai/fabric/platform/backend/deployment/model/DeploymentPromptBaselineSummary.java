package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentPromptBaselineSummary(
    String deploymentId,
    String versionId,
    String versionLabel,
    Instant publishedAt,
    int populatedPromptCount,
    JsonNode promptConfig
) {
}
