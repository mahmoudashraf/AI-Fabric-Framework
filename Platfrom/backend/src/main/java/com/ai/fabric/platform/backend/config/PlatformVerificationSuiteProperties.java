package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification.suites")
public record PlatformVerificationSuiteProperties(
    Duration timeout,
    Duration hostedStageTimeout,
    Duration scriptStageTimeout,
    Duration codeRegressionScriptTimeout,
    Duration releaseGateFreshness,
    Duration pollInterval,
    int maxRecentRuns,
    int maxStageLogCharacters,
    int codeRegressionMaxLogCharacters,
    String platformUiBaseUrl,
    String weaviateHost,
    String shopifyBridgeBaseUrl,
    String shopifyShopDomain,
    String shopifyProductServiceRef,
    String shopifyEmbeddedHost,
    String partnerUiBaseUrl
) {

    public PlatformVerificationSuiteProperties {
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
            ? Duration.ofMinutes(180)
            : timeout;
        hostedStageTimeout = hostedStageTimeout == null || hostedStageTimeout.isZero() || hostedStageTimeout.isNegative()
            ? Duration.ofMinutes(12)
            : hostedStageTimeout;
        scriptStageTimeout = scriptStageTimeout == null || scriptStageTimeout.isZero() || scriptStageTimeout.isNegative()
            ? Duration.ofMinutes(20)
            : scriptStageTimeout;
        codeRegressionScriptTimeout = codeRegressionScriptTimeout == null || codeRegressionScriptTimeout.isZero() || codeRegressionScriptTimeout.isNegative()
            ? Duration.ofMinutes(75)
            : codeRegressionScriptTimeout;
        releaseGateFreshness = releaseGateFreshness == null || releaseGateFreshness.isZero() || releaseGateFreshness.isNegative()
            ? Duration.ofHours(12)
            : releaseGateFreshness;
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
            ? Duration.ofSeconds(3)
            : pollInterval;
        maxRecentRuns = maxRecentRuns <= 0 ? 20 : maxRecentRuns;
        maxStageLogCharacters = maxStageLogCharacters <= 0 ? 12_000 : maxStageLogCharacters;
        codeRegressionMaxLogCharacters = codeRegressionMaxLogCharacters <= 0 ? 80_000 : codeRegressionMaxLogCharacters;
        platformUiBaseUrl = normalize(platformUiBaseUrl);
        weaviateHost = normalize(weaviateHost);
        shopifyBridgeBaseUrl = normalize(shopifyBridgeBaseUrl);
        shopifyShopDomain = normalize(shopifyShopDomain);
        shopifyProductServiceRef = normalize(shopifyProductServiceRef);
        shopifyEmbeddedHost = normalize(shopifyEmbeddedHost);
        partnerUiBaseUrl = normalize(partnerUiBaseUrl);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
