package com.ai.fabric.platform.backend.marketplace.model;

public record PlatformManagedInferenceHealthSummary(
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
    PlatformManagedInferenceProbeSummary healthProbe,
    PlatformManagedInferenceProbeSummary inferenceProbe
) {
}
