package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.github-actions")
public record PlatformGitHubActionsProperties(
    String apiBaseUrl,
    String verificationWorkflowFile,
    Duration verificationContextTtl,
    Duration workflowRunLookupTimeout
) {

    public PlatformGitHubActionsProperties {
        apiBaseUrl = normalize(apiBaseUrl, "https://api.github.com");
        verificationWorkflowFile = normalize(verificationWorkflowFile, "deployment-verification.yml");
        verificationContextTtl = verificationContextTtl == null || verificationContextTtl.isZero() || verificationContextTtl.isNegative()
            ? Duration.ofMinutes(20)
            : verificationContextTtl;
        workflowRunLookupTimeout = workflowRunLookupTimeout == null || workflowRunLookupTimeout.isZero() || workflowRunLookupTimeout.isNegative()
            ? Duration.ofSeconds(30)
            : workflowRunLookupTimeout;
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
