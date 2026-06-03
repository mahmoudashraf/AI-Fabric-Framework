package com.ai.fabric.platform.backend.tenant.model;

import jakarta.validation.constraints.Size;

public record UpdatePlatformConsumerBindingRequest(
    @Size(max = 64) String deploymentId,
    @Size(max = 64) String releaseId,
    @Size(max = 64) String targetProfileId,
    @Size(max = 1000) String reason
) {
    public UpdatePlatformConsumerBindingRequest(String deploymentId, String reason) {
        this(deploymentId, null, null, reason);
    }
}
