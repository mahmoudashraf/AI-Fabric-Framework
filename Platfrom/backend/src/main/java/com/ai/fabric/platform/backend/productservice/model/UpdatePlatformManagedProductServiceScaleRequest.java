package com.ai.fabric.platform.backend.productservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePlatformManagedProductServiceScaleRequest(
    @NotNull @Min(1) @Max(20) Integer desiredReplicas
) {
}

