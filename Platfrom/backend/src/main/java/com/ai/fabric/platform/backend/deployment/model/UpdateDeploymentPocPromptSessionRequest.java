package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateDeploymentPocPromptSessionRequest(
    String sessionLabel,
    JsonNode promptPreview
) {
}
