package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentGitHubVerificationDispatchRequest(
    String profile,
    Boolean verifyWrite
) {
}
