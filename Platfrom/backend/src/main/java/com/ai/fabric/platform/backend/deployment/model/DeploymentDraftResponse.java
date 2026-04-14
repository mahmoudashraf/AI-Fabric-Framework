package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentDraftResponse(
    String id,
    String deploymentId,
    int revisionNumber,
    String status,
    JsonNode actionsConfig,
    JsonNode entityConfig,
    JsonNode routingConfig,
    JsonNode providerConfig,
    JsonNode securityConfig,
    JsonNode promptConfig,
    JsonNode knowledgeSourceConfig,
    JsonNode shellConfig,
    JsonNode automationConfig,
    Instant createdAt,
    Instant updatedAt
) {
    public DeploymentDraftResponse(String id,
                                   String deploymentId,
                                   int revisionNumber,
                                   String status,
                                   JsonNode actionsConfig,
                                   JsonNode entityConfig,
                                   JsonNode routingConfig,
                                   JsonNode providerConfig,
                                   JsonNode securityConfig,
                                   JsonNode promptConfig,
                                   Instant createdAt,
                                   Instant updatedAt) {
        this(
            id,
            deploymentId,
            revisionNumber,
            status,
            actionsConfig,
            entityConfig,
            routingConfig,
            providerConfig,
            securityConfig,
            promptConfig,
            null,
            null,
            null,
            createdAt,
            updatedAt
        );
    }

    public DeploymentDraftResponse(String id,
                                   String deploymentId,
                                   int revisionNumber,
                                   String status,
                                   JsonNode actionsConfig,
                                   JsonNode entityConfig,
                                   JsonNode routingConfig,
                                   JsonNode providerConfig,
                                   JsonNode securityConfig,
                                   JsonNode promptConfig,
                                   JsonNode knowledgeSourceConfig,
                                   JsonNode shellConfig,
                                   Instant createdAt,
                                   Instant updatedAt) {
        this(
            id,
            deploymentId,
            revisionNumber,
            status,
            actionsConfig,
            entityConfig,
            routingConfig,
            providerConfig,
            securityConfig,
            promptConfig,
            knowledgeSourceConfig,
            shellConfig,
            null,
            createdAt,
            updatedAt
        );
    }
}
