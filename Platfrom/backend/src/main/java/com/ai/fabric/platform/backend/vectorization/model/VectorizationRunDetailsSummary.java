package com.ai.fabric.platform.backend.vectorization.model;

import java.util.List;

public record VectorizationRunDetailsSummary(
    String deploymentId,
    VectorizationRunSummary run,
    List<VectorizationCheckpointSummary> checkpoints,
    List<VectorizationFailureBucketSummary> failureBuckets
) {
}
