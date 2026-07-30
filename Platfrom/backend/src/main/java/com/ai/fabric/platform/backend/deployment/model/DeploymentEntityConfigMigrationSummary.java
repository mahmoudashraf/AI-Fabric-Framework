package com.ai.fabric.platform.backend.deployment.model;

import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigMigrationReport;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DeploymentEntityConfigMigrationSummary(
    String deploymentId,
    String draftId,
    String currentContractVersion,
    boolean applied,
    EntityConfigMigrationReport report,
    JsonNode migratedConfig,
    Instant evaluatedAt
) {
}
