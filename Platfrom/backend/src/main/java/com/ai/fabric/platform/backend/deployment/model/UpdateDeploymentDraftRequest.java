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
    JsonNode shellConfig,
    JsonNode automationConfig,
    JsonNode marketplaceDatasetConfig
) {
    public UpdateDeploymentDraftRequest(JsonNode actionsConfig,
                                        JsonNode entityConfig,
                                        JsonNode routingConfig,
                                        JsonNode providerConfig,
                                        JsonNode securityConfig,
                                        JsonNode promptConfig) {
        this(actionsConfig, entityConfig, routingConfig, providerConfig, securityConfig, promptConfig, null, null, null, null);
    }

    public UpdateDeploymentDraftRequest(JsonNode actionsConfig,
                                        JsonNode entityConfig,
                                        JsonNode routingConfig,
                                        JsonNode providerConfig,
                                        JsonNode securityConfig,
                                        JsonNode promptConfig,
                                        JsonNode knowledgeSourceConfig,
                                        JsonNode shellConfig) {
        this(actionsConfig, entityConfig, routingConfig, providerConfig, securityConfig, promptConfig, knowledgeSourceConfig, shellConfig, null, null);
    }

    public UpdateDeploymentDraftRequest(JsonNode actionsConfig,
                                        JsonNode entityConfig,
                                        JsonNode routingConfig,
                                        JsonNode providerConfig,
                                        JsonNode securityConfig,
                                        JsonNode promptConfig,
                                        JsonNode knowledgeSourceConfig,
                                        JsonNode shellConfig,
                                        JsonNode automationConfig) {
        this(actionsConfig, entityConfig, routingConfig, providerConfig, securityConfig, promptConfig, knowledgeSourceConfig, shellConfig, automationConfig, null);
    }
}
