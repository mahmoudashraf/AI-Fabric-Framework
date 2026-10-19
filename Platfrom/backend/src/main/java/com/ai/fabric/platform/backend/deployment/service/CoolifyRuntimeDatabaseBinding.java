package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyRuntimeDatabaseBinding(
    String mode,
    CoolifyDatabaseSummary database,
    String jdbcUrl,
    String username,
    String password,
    String passwordSecretName,
    String bindingId,
    String fingerprint,
    String runtimeSchema,
    String connectorSchema,
    String connectorJdbcUrl
) {
    public CoolifyRuntimeDatabaseBinding(String mode, CoolifyDatabaseSummary database, String jdbcUrl,
                                         String username, String password, String passwordSecretName) {
        this(mode, database, jdbcUrl, username, password, passwordSecretName, null, null,
            null, "integration_connector", jdbcUrl);
    }

    public boolean managed() { return database != null; }

    @Override public String toString() { return "CoolifyRuntimeDatabaseBinding[mode=" + mode + ", connection redacted]"; }
}
