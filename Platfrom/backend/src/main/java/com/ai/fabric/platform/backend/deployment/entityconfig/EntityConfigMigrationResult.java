package com.ai.fabric.platform.backend.deployment.entityconfig;

import com.fasterxml.jackson.databind.node.ObjectNode;

public record EntityConfigMigrationResult(
    ObjectNode migratedConfig,
    EntityConfigMigrationReport report
) {
}
