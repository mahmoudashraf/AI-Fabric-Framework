package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.provisioning")
public record PlatformProvisioningProperties(
    String mode,
    String railwayApiEndpoint,
    String railwayApiToken,
    String repository,
    String branch,
    String environmentName,
    String workspaceId,
    String runtimeServiceRoot,
    String runtimeDockerfilePath,
    String connectorServiceRoot,
    String connectorDockerfilePath,
    String runtimeServiceNamePrefix,
    String connectorServiceNamePrefix,
    int projectNameMaxLength,
    String corsAllowedOrigins,
    String corsAllowedOriginPatterns,
    boolean corsAllowCredentials,
    boolean runtimeDevDefaultsEnabled,
    int runtimeProxyTimeoutMs,
    java.time.Duration deploymentPollInterval,
    java.time.Duration deploymentTimeout
) {

    public PlatformProvisioningProperties {
        mode = defaultText(mode, "RAILWAY_STUB");
        railwayApiEndpoint = defaultText(railwayApiEndpoint, "https://backboard.railway.com/graphql/v2");
        railwayApiToken = railwayApiToken == null ? "" : railwayApiToken.trim();
        repository = defaultText(repository, "TheBaseRepo");
        branch = defaultText(branch, "main");
        environmentName = defaultText(environmentName, "dev");
        workspaceId = workspaceId == null ? "" : workspaceId.trim();
        runtimeServiceRoot = defaultText(runtimeServiceRoot, "ai-infrastructure-module/ai-fabric-runtime");
        runtimeDockerfilePath = defaultText(
            runtimeDockerfilePath,
            "ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile"
        );
        connectorServiceRoot = defaultText(
            connectorServiceRoot,
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector"
        );
        connectorDockerfilePath = defaultText(
            connectorDockerfilePath,
            "ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile"
        );
        runtimeServiceNamePrefix = defaultText(runtimeServiceNamePrefix, "runtime");
        connectorServiceNamePrefix = defaultText(connectorServiceNamePrefix, "rest-connector");
        projectNameMaxLength = projectNameMaxLength <= 0 ? 32 : projectNameMaxLength;
        corsAllowedOrigins = corsAllowedOrigins == null ? "" : corsAllowedOrigins.trim();
        corsAllowedOriginPatterns = corsAllowedOriginPatterns == null ? "" : corsAllowedOriginPatterns.trim();
        runtimeProxyTimeoutMs = runtimeProxyTimeoutMs <= 0 ? 60_000 : runtimeProxyTimeoutMs;
        deploymentPollInterval = deploymentPollInterval == null ? java.time.Duration.ofSeconds(5) : deploymentPollInterval;
        deploymentTimeout = deploymentTimeout == null ? java.time.Duration.ofMinutes(10) : deploymentTimeout;
    }

    private static String defaultText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
