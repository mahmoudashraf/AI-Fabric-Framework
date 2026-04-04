package com.ai.fabric.platform.backend.vectorization.model;

import java.util.List;

public record VectorizationVerificationRunDetailsSummary(
    String deploymentId,
    VectorizationVerificationRunSummary verificationRun,
    List<VectorizationVerificationStepSummary> steps,
    VectorizationRunSummary linkedVectorizationRun
) {
}
