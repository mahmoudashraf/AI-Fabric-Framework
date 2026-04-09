package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification")
public record PlatformVerificationProperties(
    Duration timeout,
    Duration runtimeIndexingOverviewTimeout,
    String runtimeHealthPath,
    String connectorHealthPath,
    String runtimeConnectorHealthPath,
    String runtimeAdminOverviewPath,
    String runtimeAuthOverviewPath,
    String runtimeActionsOverviewPath,
    String runtimeIndexingOverviewPath,
    String connectorAdminOverviewPath,
    String connectorActionsOverviewPath
) {

    public PlatformVerificationProperties {
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        runtimeIndexingOverviewTimeout = runtimeIndexingOverviewTimeout == null
            ? max(timeout, Duration.ofSeconds(10))
            : runtimeIndexingOverviewTimeout;
        runtimeHealthPath = normalizePath(runtimeHealthPath, "/actuator/health");
        connectorHealthPath = normalizePath(connectorHealthPath, "/actuator/health");
        runtimeConnectorHealthPath = normalizePath(runtimeConnectorHealthPath, "/api/admin/connector/health");
        runtimeAdminOverviewPath = normalizePath(runtimeAdminOverviewPath, "/api/admin/overview");
        runtimeAuthOverviewPath = normalizePath(runtimeAuthOverviewPath, "/api/admin/auth/overview");
        runtimeActionsOverviewPath = normalizePath(runtimeActionsOverviewPath, "/api/admin/actions/overview");
        runtimeIndexingOverviewPath = normalizePath(runtimeIndexingOverviewPath, "/api/admin/indexing/overview");
        connectorAdminOverviewPath = normalizePath(connectorAdminOverviewPath, "/api/admin/connector/overview");
        connectorActionsOverviewPath = normalizePath(connectorActionsOverviewPath, "/api/admin/connector/actions/overview");
    }

    public PlatformVerificationProperties(Duration timeout,
                                          String runtimeHealthPath,
                                          String connectorHealthPath,
                                          String runtimeConnectorHealthPath,
                                          String runtimeAdminOverviewPath,
                                          String runtimeAuthOverviewPath,
                                          String runtimeActionsOverviewPath,
                                          String runtimeIndexingOverviewPath,
                                          String connectorAdminOverviewPath,
                                          String connectorActionsOverviewPath) {
        this(
            timeout,
            null,
            runtimeHealthPath,
            connectorHealthPath,
            runtimeConnectorHealthPath,
            runtimeAdminOverviewPath,
            runtimeAuthOverviewPath,
            runtimeActionsOverviewPath,
            runtimeIndexingOverviewPath,
            connectorAdminOverviewPath,
            connectorActionsOverviewPath
        );
    }

    private static String normalizePath(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static Duration max(Duration left, Duration right) {
        return left.compareTo(right) >= 0 ? left : right;
    }
}
