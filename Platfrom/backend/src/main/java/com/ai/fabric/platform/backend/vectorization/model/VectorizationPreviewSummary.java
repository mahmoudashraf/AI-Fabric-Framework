package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

public record VectorizationPreviewSummary(
    String deploymentId,
    String syncState,
    JsonNode sourceDiscovery,
    JsonNode entityScope,
    JsonNode mappingConfig,
    JsonNode executionConfig,
    JsonNode reindexOptions,
    String indexedOutputHash
) {
}
