package com.ai.fabric.platform.backend.tenant.model;

import jakarta.validation.constraints.Size;

public record UpdatePlatformConsumerBindingRequest(
    @Size(max = 64) String deploymentId,
    @Size(max = 1000) String reason
) {
}
