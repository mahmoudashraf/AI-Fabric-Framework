package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateDeploymentDraftRequest(
    JsonNode actionsConfig,
    JsonNode entityConfig,
    JsonNode routingConfig,
    JsonNode providerConfig,
    JsonNode securityConfig
) {
}

