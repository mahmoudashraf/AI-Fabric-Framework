package com.ai.fabric.platform.backend.deployment.entityconfig;

import java.util.List;

public record EntityConfigMigrationReport(
    String sourceContractVersion,
    String targetContractVersion,
    boolean migrationRequired,
    boolean blocked,
    String beforeHash,
    String afterHash,
    List<String> convertedEntityTypes,
    List<String> droppedKeys,
    List<EntityConfigMigrationMessage> warnings,
    List<EntityConfigMigrationMessage> blockers,
    boolean vectorRebuildRequired
) {

    public EntityConfigMigrationReport {
        convertedEntityTypes = List.copyOf(convertedEntityTypes);
        droppedKeys = List.copyOf(droppedKeys);
        warnings = List.copyOf(warnings);
        blockers = List.copyOf(blockers);
    }
}
