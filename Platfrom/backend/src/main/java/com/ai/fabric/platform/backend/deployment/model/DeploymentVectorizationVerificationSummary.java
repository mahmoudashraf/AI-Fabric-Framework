package com.ai.fabric.platform.backend.deployment.model;

import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;

import java.util.List;

public record DeploymentVectorizationVerificationSummary(
    String deploymentId,
    boolean planPresent,
    boolean sourceConnectionPresent,
    boolean activeRevisionPresent,
    boolean configured,
    boolean runnerPresent,
    boolean runnerRequired,
    boolean platformManagedRunnerExpected,
    List<String> availableEntityTypes,
    List<String> entityScope,
    VectorizationSourceConnectionSummary sourceConnection,
    VectorizationPlanSummary plan,
    VectorizationRunnerSummary runner
) {
}
