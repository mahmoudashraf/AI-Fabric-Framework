package com.ai.fabric.platform.backend.deployment.model;

public record SubmitDeploymentHumanReviewDecisionRequest(
    String decision,
    long expectedVersion,
    String decisionId
) {
}
