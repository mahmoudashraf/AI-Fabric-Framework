package com.ai.fabric.platform.backend.vectorization.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateVectorizationVerificationRunRequest(
    @NotBlank String verificationType,
    List<String> entityTypes,
    String note,
    String counterpartDeploymentId
) {
}
