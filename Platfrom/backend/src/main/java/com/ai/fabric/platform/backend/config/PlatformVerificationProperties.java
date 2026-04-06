package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification")
public record PlatformVerificationProperties(
    Duration timeout,
    String runtimeHealthPath,
    String connectorHealthPath,
    String runtimeAdminOverviewPath,
    String runtimeActionsOverviewPath,
    String runtimeIndexingOverviewPath,
    String connectorAdminOverviewPath,
    String connectorActionsOverviewPath
) {

    public PlatformVerificationProperties {
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        runtimeHealthPath = normalizePath(runtimeHealthPath, "/actuator/health");
        connectorHealthPath = normalizePath(connectorHealthPath, "/actuator/health");
        runtimeAdminOverviewPath = normalizePath(runtimeAdminOverviewPath, "/api/admin/overview");
        runtimeActionsOverviewPath = normalizePath(runtimeActionsOverviewPath, "/api/admin/actions/overview");
        runtimeIndexingOverviewPath = normalizePath(runtimeIndexingOverviewPath, "/api/admin/indexing/overview");
        connectorAdminOverviewPath = normalizePath(connectorAdminOverviewPath, "/api/admin/connector/overview");
        connectorActionsOverviewPath = normalizePath(connectorActionsOverviewPath, "/api/admin/connector/actions/overview");
    }

    private static String normalizePath(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.startsWith("/") ? value : "/" + value;
    }
}
