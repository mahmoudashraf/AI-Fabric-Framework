package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

public record UpdateDeploymentDraftRequest(
    JsonNode actionsConfig,
    JsonNode entityConfig,
    JsonNode routingConfig,
    JsonNode providerConfig,
    JsonNode securityConfig,
    JsonNode promptConfig,
    JsonNode knowledgeSourceConfig,
    JsonNode shellConfig
) {
    public UpdateDeploymentDraftRequest(JsonNode actionsConfig,
                                        JsonNode entityConfig,
                                        JsonNode routingConfig,
                                        JsonNode providerConfig,
                                        JsonNode securityConfig,
                                        JsonNode promptConfig) {
        this(actionsConfig, entityConfig, routingConfig, providerConfig, securityConfig, promptConfig, null, null);
    }
}
