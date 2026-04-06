package com.ai.fabric.platform.backend.vectorization.model;

public record VectorizationRunnerClaimSummary(
    String sessionStatus,
    VectorizationRunSummary run
) {
}
