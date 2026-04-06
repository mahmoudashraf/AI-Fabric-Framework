package com.ai.fabric.platform.backend.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateVectorizationRunRequest(
    @NotBlank String reason,
    List<String> entityTypes,
    String note,
    JsonNode executionOverrides
) {
}
