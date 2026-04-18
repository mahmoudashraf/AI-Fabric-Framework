package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceHealthSummary(
    String serviceRef,
    String status,
    boolean railwayLifecycleManaged,
    boolean secretConfigured,
    String driftStatus,
    String driftMessage,
    String lastHealthyAt,
    String lastProbeAt,
    String lastSuccessfulProbeAt,
    String lastFailedProbeAt,
    String lastProbeStatus,
    String lastProbeMessage,
    PlatformManagedProductServiceProbeSummary healthProbe
) {
}

