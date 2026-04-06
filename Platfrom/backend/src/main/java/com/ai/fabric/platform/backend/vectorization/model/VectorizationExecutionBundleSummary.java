package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

public record VectorizationExecutionBundleSummary(
    String deploymentId,
    String runId,
    String reason,
    String planRevisionId,
    String runnerMode,
    JsonNode entityScope,
    JsonNode mappingConfig,
    JsonNode executionConfig,
    JsonNode connectionDescriptor,
    JsonNode sourceAuth,
    JsonNode localSecretAliases,
    JsonNode targetDescriptor,
    JsonNode targetAuth
) {
}
