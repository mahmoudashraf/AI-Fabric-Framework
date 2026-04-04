package com.ai.fabric.platform.backend.vectorization.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateVectorizationSyncStateRequest(
    @NotBlank String action,
    String reason
) {
}
