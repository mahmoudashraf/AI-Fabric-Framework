package com.ai.fabric.platform.backend.deployment.model;

import java.util.Map;

public record DeploymentHostedVerificationContextSummary(
    String profile,
    String script,
    String deploymentId,
    String releaseId,
    String deploymentVersionId,
    boolean verifyWrite,
    Map<String, String> env
) {
}
