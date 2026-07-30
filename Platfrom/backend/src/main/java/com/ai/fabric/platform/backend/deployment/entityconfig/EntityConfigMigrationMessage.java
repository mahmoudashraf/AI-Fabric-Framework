package com.ai.fabric.platform.backend.deployment.entityconfig;

public record EntityConfigMigrationMessage(
    String code,
    String path,
    String message
) {
}
