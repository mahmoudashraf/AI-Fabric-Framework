package com.ai.fabric.platform.backend.vectorization.model;

public record RotateVectorizationRunnerTokenRequest(
    Integer validityHours,
    String runnerMode
) {
}
