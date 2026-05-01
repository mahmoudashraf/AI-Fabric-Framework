package com.ai.fabric.platform.backend.deployment.model;

public record PatchDeploymentTargetProfileRequest(
    Boolean active,
    Boolean defaultForRuntime,
    Boolean defaultForRestartableServices
) {
}
