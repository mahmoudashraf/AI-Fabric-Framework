package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "platform.ai-workspace")
public record PlatformAIWorkspaceProperties(
    Path assetRoot,
    boolean allowLoopbackHttp,
    int manifestRequestsPerMinute,
    Duration manifestCacheTtl,
    String authenticatedBrokerUrl,
    String shopifyBridgeBaseUrl
) {

    public PlatformAIWorkspaceProperties {
        assetRoot = assetRoot == null ? Path.of("/app/ai-workspace-assets") : assetRoot;
        manifestRequestsPerMinute = manifestRequestsPerMinute <= 0 ? 120 : manifestRequestsPerMinute;
        manifestCacheTtl = manifestCacheTtl == null || manifestCacheTtl.isNegative() || manifestCacheTtl.isZero()
            ? Duration.ofSeconds(60)
            : manifestCacheTtl;
        authenticatedBrokerUrl = normalizeBaseUrl(authenticatedBrokerUrl);
        shopifyBridgeBaseUrl = normalizeBaseUrl(shopifyBridgeBaseUrl);
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
