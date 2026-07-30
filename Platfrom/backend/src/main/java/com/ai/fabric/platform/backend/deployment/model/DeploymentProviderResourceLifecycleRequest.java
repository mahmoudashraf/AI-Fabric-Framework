package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.Size;

public record DeploymentProviderResourceLifecycleRequest(
    @Size(max = 64) String status,
    @Size(max = 1000) String reason
) {
}
