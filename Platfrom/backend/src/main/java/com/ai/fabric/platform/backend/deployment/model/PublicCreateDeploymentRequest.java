package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record PublicCreateDeploymentRequest(
    @NotBlank String externalDeploymentKey,
    @NotBlank String name,
    @NotBlank String environment,
    @NotBlank String templateId,
    boolean autoApply,
    JsonNode callbackMetadata
) {
}
