package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyRuntimeDatabaseBinding(
    String mode,
    CoolifyDatabaseSummary database,
    String jdbcUrl,
    String username,
    String password,
    String passwordSecretName
) {
}
