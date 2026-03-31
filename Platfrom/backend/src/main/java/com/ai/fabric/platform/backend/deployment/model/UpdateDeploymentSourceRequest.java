package com.ai.fabric.platform.backend.deployment.model;

public record UpdateDeploymentSourceRequest(
    String repository,
    String branch
) {
}
