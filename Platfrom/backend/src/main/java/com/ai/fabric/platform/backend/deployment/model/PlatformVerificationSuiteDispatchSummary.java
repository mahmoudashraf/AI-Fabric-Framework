package com.ai.fabric.platform.backend.deployment.model;

public record PlatformVerificationSuiteDispatchSummary(
    String suiteKey,
    String summaryMessage,
    PlatformVerificationSuiteRunSummary run
) {
}
