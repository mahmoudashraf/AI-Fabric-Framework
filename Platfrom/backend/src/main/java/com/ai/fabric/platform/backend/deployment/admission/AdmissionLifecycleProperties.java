package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.deployment.admission.lifecycle")
public record AdmissionLifecycleProperties(
    boolean workerEnabled,
    Duration initialDelay,
    Duration pollDelay,
    int batchSize,
    String noticeTransportReference,
    boolean consoleEnabled,
    boolean ownerEmailEnabled,
    String ownerEmailFrom,
    boolean retirementWorkerEnabled
) {
    public AdmissionLifecycleProperties {
        initialDelay = positive(initialDelay, Duration.ofSeconds(30));
        pollDelay = positive(pollDelay, Duration.ofSeconds(30));
        batchSize = batchSize <= 0 ? 32 : Math.min(batchSize, 256);
        noticeTransportReference = text(noticeTransportReference, "loomai-admission-console-smtp-v1");
        ownerEmailFrom = ownerEmailFrom == null ? "" : ownerEmailFrom.trim();
    }

    private static Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
