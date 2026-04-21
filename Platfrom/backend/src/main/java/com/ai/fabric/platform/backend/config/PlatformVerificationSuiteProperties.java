package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.verification.suites")
public record PlatformVerificationSuiteProperties(
    Duration timeout,
    Duration hostedStageTimeout,
    Duration pollInterval,
    int maxRecentRuns
) {

    public PlatformVerificationSuiteProperties {
        timeout = timeout == null || timeout.isZero() || timeout.isNegative()
            ? Duration.ofMinutes(60)
            : timeout;
        hostedStageTimeout = hostedStageTimeout == null || hostedStageTimeout.isZero() || hostedStageTimeout.isNegative()
            ? Duration.ofMinutes(12)
            : hostedStageTimeout;
        pollInterval = pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
            ? Duration.ofSeconds(3)
            : pollInterval;
        maxRecentRuns = maxRecentRuns <= 0 ? 20 : maxRecentRuns;
    }
}
