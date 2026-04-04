package com.ai.fabric.platform.backend.vectorization.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateVectorizationRunRequest(
    @NotBlank String reason,
    List<String> entityTypes,
    String note
) {
}
