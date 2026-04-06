package com.ai.fabric.vectorization.model;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record VectorizationExecutionBundle(
    String deploymentId,
    String runId,
    VectorizationRunReason reason,
    String planRevisionId,
    VectorizationRunnerMode runnerMode,
    String sourceConnectionId,
    List<String> entityScope,
    JsonNode mappingConfig,
    JsonNode executionConfig,
    ConnectionDescriptor connectionDescriptor,
    ResolvedSourceAuthMaterial sourceAuthMaterial,
    TargetConnectionDescriptor targetConnection
) {
}
