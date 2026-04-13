package com.ai.fabric.platform.backend.deployment.model;

public record PublicDeploymentAccessSummary(
    String runtimeExposure,
    String connectorExposure,
    PublicRuntimePostureSummary posture,
    PublicRuntimeEndpointsSummary runtime,
    PublicTrustedBackendAccessSummary trustedBackend,
    PublicRuntimeTokenSummary publicRuntime,
    String guidance
) {
}
