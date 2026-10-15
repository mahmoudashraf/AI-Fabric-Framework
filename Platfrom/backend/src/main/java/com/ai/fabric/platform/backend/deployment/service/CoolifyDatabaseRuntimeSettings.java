package com.ai.fabric.platform.backend.deployment.service;

/** Provider-facing PostgreSQL container limits supported by Coolify. */
public record CoolifyDatabaseRuntimeSettings(
    String limitsMemory,
    String limitsMemorySwap,
    String limitsMemoryReservation,
    String limitsCpus,
    String limitsCpuSet
) {
}
