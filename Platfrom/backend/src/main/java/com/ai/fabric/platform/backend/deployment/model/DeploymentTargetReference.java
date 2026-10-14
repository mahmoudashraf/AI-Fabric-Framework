package com.ai.fabric.platform.backend.deployment.model;

public record DeploymentTargetReference(String deploymentId, String targetProfileId, String releaseId) {
}
