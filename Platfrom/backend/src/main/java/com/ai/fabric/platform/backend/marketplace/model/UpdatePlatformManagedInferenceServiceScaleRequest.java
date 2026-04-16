package com.ai.fabric.platform.backend.marketplace.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdatePlatformManagedInferenceServiceScaleRequest(
    @Min(1) @Max(20) Integer desiredReplicas
) {
}
