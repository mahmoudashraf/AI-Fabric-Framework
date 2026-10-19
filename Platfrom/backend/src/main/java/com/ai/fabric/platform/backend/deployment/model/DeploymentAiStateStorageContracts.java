package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** Customer PostgreSQL is an AI-state integration, never a business-system or infrastructure ownership grant. */
public final class DeploymentAiStateStorageContracts {
    private DeploymentAiStateStorageContracts() { }

    public record Request(String versionId,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String host,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) Integer port,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String databaseName,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String runtimeSchema,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String connectorSchema,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String username,
                          @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
                          Boolean migrationAuthorized, String approvalReference) {
        @Override public String toString() { return "AiStateStorageRequest[connection redacted]"; }
    }

    public record Summary(String bindingId, String customerId, String tenantId, String deploymentId,
                          String targetProfileId, String versionId, String configHash, String mode,
                          String lifecycle, boolean migrationAuthorized, String fingerprint, Instant checkedAt) { }
}
