package com.ai.fabric.platform.backend.tenant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePlatformConsumerRequest(
    @NotBlank @Size(min = 3, max = 128) String consumerId,
    @NotBlank @Size(min = 2, max = 255) String displayName,
    @Size(max = 1000) String description,
    @Size(max = 64) String deploymentId,
    @Size(max = 1000) String bindingReason
) {
}
