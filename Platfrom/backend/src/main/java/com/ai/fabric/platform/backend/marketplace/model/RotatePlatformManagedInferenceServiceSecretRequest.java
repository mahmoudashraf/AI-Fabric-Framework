package com.ai.fabric.platform.backend.marketplace.model;

import jakarta.validation.constraints.NotBlank;

public record RotatePlatformManagedInferenceServiceSecretRequest(
    @NotBlank String value
) {
}
