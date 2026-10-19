package com.ai.fabric.platform.backend.deployment.service;

/** Private material: never include this value in response DTOs, provider details, errors or audit events. */
public record DeploymentAiStateStorageConnection(String host, int port, String databaseName,
                                                 String runtimeSchema, String connectorSchema,
                                                 String username, String password) {
    public String runtimeJdbcUrl() { return jdbcUrl(runtimeSchema); }
    public String connectorJdbcUrl() { return jdbcUrl(connectorSchema); }

    private String jdbcUrl(String schema) {
        return "jdbc:postgresql://" + host + ":" + port + "/" + databaseName
            + "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"
            + "&connectTimeout=10&socketTimeout=15&currentSchema=" + schema;
    }

    @Override public String toString() { return "AiStateStorageConnection[redacted]"; }
}
