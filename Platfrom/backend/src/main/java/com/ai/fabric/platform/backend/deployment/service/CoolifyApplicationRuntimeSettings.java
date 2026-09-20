package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyApplicationRuntimeSettings(
    Integer healthCheckIntervalSeconds,
    Integer healthCheckTimeoutSeconds,
    Integer healthCheckRetries,
    Integer healthCheckStartPeriodSeconds,
    String limitsMemory,
    String limitsMemorySwap,
    String limitsMemoryReservation,
    String limitsCpus
) {
}
