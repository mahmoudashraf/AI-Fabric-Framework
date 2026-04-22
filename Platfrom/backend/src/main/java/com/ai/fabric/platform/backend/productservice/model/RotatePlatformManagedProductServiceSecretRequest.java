package com.ai.fabric.platform.backend.productservice.model;

import jakarta.validation.constraints.NotBlank;

public record RotatePlatformManagedProductServiceSecretRequest(
    @NotBlank String value
) {
}

