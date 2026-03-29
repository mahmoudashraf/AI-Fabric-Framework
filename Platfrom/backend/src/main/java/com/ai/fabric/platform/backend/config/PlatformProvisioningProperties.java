package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.provisioning")
public record PlatformProvisioningProperties(
    String mode,
    String repository,
    String branch,
    String environmentName,
    String workspaceId,
    String runtimeServiceRoot,
    String connectorServiceRoot,
    String runtimeServiceNamePrefix,
    String connectorServiceNamePrefix
) {

    public PlatformProvisioningProperties {
        mode = defaultText(mode, "RAILWAY_STUB");
        repository = defaultText(repository, "TheBaseRepo");
        branch = defaultText(branch, "main");
        environmentName = defaultText(environmentName, "dev");
        workspaceId = workspaceId == null ? "" : workspaceId.trim();
        runtimeServiceRoot = defaultText(runtimeServiceRoot, "ai-infrastructure-module/ai-fabric-runtime");
        connectorServiceRoot = defaultText(
            connectorServiceRoot,
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector"
        );
        runtimeServiceNamePrefix = defaultText(runtimeServiceNamePrefix, "runtime");
        connectorServiceNamePrefix = defaultText(connectorServiceNamePrefix, "rest-connector");
    }

    private static String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
